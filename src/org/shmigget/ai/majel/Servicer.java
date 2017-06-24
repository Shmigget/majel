package org.shmigget.ai.majel;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.shmigget.ai.majel.Categorizer.InputCategoryEnum;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Servicer
{
    private final static String ALPHABETLOWERCASE = 
        "'abcdefghijklmnopqrstuvwxyz'";
    private final static String ALPHABETUPPERCASE = 
        "'ABCDEFGHIJKLMNOPQRSTUVWXYZ'";
    /**
     * Base URL for hitting the description service.
     */
    private final static String BASEDESCURL = 
        "http://en.wikipedia.org/w/api.php?action=query&prop=extracts&" +
        "format=xml&exintro&explaintext&titles=";
    /**
     * Disambiguation description URL.
     */
    private final static String BASEDESCDISAMBIGUATIONURL = 
        "http://en.wikipedia.org/w/api.php?action=query&prop=extracts&" +
        "format=xml&explaintext&titles=";    
    /**
     * Base URL for hitting the definition service.
     */
    private final static String BASEDICTIONARYURL = 
        "http://www.stands4.com/services/v2/defs.php?uid=2192&" +
        "tokenid=hrBimp2yIr610FQt&word=";
//    private final static String BASEDICTIONARYURL = 
//        "http://en.wiktionary.org/w/api.php?action=query&prop=extracts&" +
//        "format=xml&explaintext&titles=";
    private final static String BASENOTFOUNDDESCSTART = 
        "The description of ";
    private final static String BASENOTFOUNDDICTIONARYSTART = 
        "The definition of ";
    private final static String BASENOTFOUNDEND = " could not be found";
    private final static String PAUSE = "pause";
    private final static String MUSICLIBRARYLOCATION = "resources/library.xml";
    final static String NOTERMMUSIC = "It appears that you requested " +
        "audio be played but did not provide an artist, album, or song.";
    final static String NOTERMWORD = "It appears that you requested " +
        "information about a word or term but didn't provide one.  Please " +
        "remember to enclose the word or term in double quotation marks.";    
    private final static String REDIRECT = "REDIRECT";
    private final static String REFER = "may refer to";
    private Pattern redirectedTermPattern = null;
    
    /**
     * My goodness, if Java supported regexps like Perl the following line would
     * do more and save me from having to do the split I'm forced to do later.
     * What's so hard about this?
     *      \s+([^\n]+).*
     * Whether I set the MULTILINE flag or not the sad excuse for a regexp 
     * engine wouldn't match on the output returned by "dogs".
     */
    private final static String REDIRECTTERMREGEX = REDIRECT + "\\s+(.*)";
    private FileInputStream musicLibraryIS = null;
    
    /**
     * Constructor
     */
    Servicer()
    {
        this.redirectedTermPattern = Pattern.compile(REDIRECTTERMREGEX);
        
        try
        {
            this.musicLibraryIS = new FileInputStream(MUSICLIBRARYLOCATION);
        }
        catch (FileNotFoundException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Entrypoint for servicing of requests.
     * 
     * @param category
     * @param input
     * @return String containing content for the user
     */
    String serviceRequest(InputCategoryEnum category, String input, 
        Teacher teacher)
    {
        String output = null;
        
        // If we started out disambiguating we can now stop.
        Majel.isDisambiguating = false;
        
        String term = this.getTerm(input);
        
        // If a correction and no term given then retrieve the most recent 
        // request term.  E.g. No, I wanted a definition of that word.
        if (term.equals("") && (
            category == InputCategoryEnum.MUSICCORRECTION || 
            category == InputCategoryEnum.DEFINITIONCORRECTION ||
            category == InputCategoryEnum.DESCRIPTIONCORRECTION))
        {
            term = teacher.getMostRecentRequestTerm();
        }
        else if (term.equals("") && category == InputCategoryEnum.MUSIC)
        {
            // Are we being asked to pause the music?
            if (Pattern.compile(Pattern.quote(PAUSE), 
                Pattern.CASE_INSENSITIVE).matcher(input).find())
            {
                return "Music paused.";
            }
            else
            {
                Majel.setWasInputValid(false);
                return NOTERMMUSIC;
            }
        }
                
        switch(category)
        {
            case MUSICCORRECTION:
            case MUSIC:
                output = this.doMusicLookup(term, category);
                break;
            case DEFINITIONCORRECTION:
            case DEFINITION:
                output = this.doWordLookup(term, category);
                break;
            case DESCRIPTIONCORRECTION:
            case DESCRIPTION:
                output = this.doWordLookup(term, category);
                break;
        }    
        
        return output;
    }
    
    /**
     * Performs music lookup.
     * @param term
     * @param category
     * @return
     */
    private String doMusicLookup(String term, InputCategoryEnum category)
    {
        String output = null;
        
        // In this case I'm grabbing an iTunes library.xml file as my music
        // library.
        output = this.parseResult(this.musicLibraryIS, category, term);
        
        return output;
    }
    
    /**
     * Performs dictionary and description lookup.
     * @param term
     * @param category
     * @return
     */
    private String doWordLookup(String term, InputCategoryEnum category)
    {
        String output = null;
        
        // Validate that we were given a term
        if (!term.equals(""))
        { 
            try
            {
                // URL encode the search term.
                String uriEncodedTerm = URLEncoder.encode(term, Majel.getEncoding());
                
                URI uri = null;
                if (category.equals(InputCategoryEnum.DEFINITION) || 
                    category.equals(InputCategoryEnum.DEFINITIONCORRECTION))
                {
                    uri = new URI(BASEDICTIONARYURL + uriEncodedTerm);
                }
                else if (category.equals(InputCategoryEnum.DESCRIPTION) ||
                    category.equals(InputCategoryEnum.DESCRIPTIONCORRECTION))
                {
                    if (Majel.isDisambiguating == false)
                    {
                        uri = new URI(BASEDESCURL + uriEncodedTerm);
                    }
                    else
                    {
                        uri = new URI(BASEDESCDISAMBIGUATIONURL + 
                            uriEncodedTerm);
                    }
                }
                Proxy proxy = this.getProxy(uri);
                URL url = uri.toURL();
                URLConnection connection = 
                    url.openConnection(proxy);
                output = 
                    this.parseResult(connection.getInputStream(), category, 
                        term);
                if (output == null || output.equals(""))
                {
                    if (category.equals(InputCategoryEnum.DEFINITION) ||
                        category.equals(InputCategoryEnum.DEFINITIONCORRECTION))
                    {
                        output = BASENOTFOUNDDICTIONARYSTART + term + 
                            BASENOTFOUNDEND;
                    }
                    else if (category.equals(InputCategoryEnum.DESCRIPTION) ||
                        category.equals(InputCategoryEnum.DESCRIPTIONCORRECTION))
                    {
                        output = BASENOTFOUNDDESCSTART + term + 
                            BASENOTFOUNDEND;                    
                    }
                }
            }
            catch (MalformedURLException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (URISyntaxException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
        }
        else
        {
            Majel.setWasInputValid(false);
            output = NOTERMWORD; 
        }

        return output;
    }
    
    /**
     * Gets the term of interest from the input string.
     * @param input
     * @return
     */
    private String getTerm(String input)
    {
        String term = "";
        
        // The term we're looking up, if it's present, is the one in input 
        // that's wrapped in quotes.  It isn't present in most corrections or 
        // when asked to pause music.
        int startQuote = input.indexOf('"');
        int endQuote = input.lastIndexOf('"');
        if (startQuote != -1 && endQuote != -1)
        {
            term = input.substring(startQuote + 1, endQuote);
        }

        return term;
    }
    
    /**
     * Grabs a proxy that may be set in the environment.
     * 
     * @param uri
     * @return Proxy 
     */
    private Proxy getProxy(URI uri)
    {
        Proxy proxy = null;
        List<Proxy> l = ProxySelector.getDefault().select(uri);
        
        for (Iterator<Proxy> iter = l.iterator(); iter.hasNext(); ) 
        {
            proxy = (Proxy) iter.next();        
        }
        return proxy;
    }
    
    /**
     * Central method for parsing results from the service lookups.
     * 
     * @param result
     * @param category
     * @return
     */
    private String parseResult(InputStream result, InputCategoryEnum category, 
        String term)
    {
        String output = "";
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try
        {
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(result);
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            if (category.equals(InputCategoryEnum.DEFINITION) || 
                category.equals(InputCategoryEnum.DEFINITIONCORRECTION))
            {
                XPathExpression expr = 
                    xpath.compile("/results/result/definition/text()");
                NodeList nodes = (NodeList)expr.evaluate(doc, 
                    XPathConstants.NODESET);
                for (int i = 0; i < nodes.getLength(); i++) 
                {
                    if (nodes.item(i).getNodeValue() != null)
                        output += (nodes.item(i).getNodeValue()) + "\n"; 
                }
            }
            else if (category.equals(InputCategoryEnum.DESCRIPTION) ||
                category.equals(InputCategoryEnum.DESCRIPTIONCORRECTION))
            {
                XPathExpression expr = 
                    xpath.compile("/api/query/pages/page/extract/text()");
                output = (String)expr.evaluate(doc, 
                    XPathConstants.STRING);
                
                // Is this a redirect?  e.g. "owls" redirects to owl.
                if (output.indexOf(REDIRECT) != -1)
                {
                    // Grab the new term, e.g. REDIRECT owl
                    Matcher m = this.redirectedTermPattern.matcher(
                        output.split("\\n")[0]);
                    if (m.matches())
                    {
                        term = m.group(1);
                        System.out.println("new term is " + term);
                    }
                    
                    // Now redo the lookup.
                    output = this.doWordLookup(term, category);
                }
                // Or is this a refer?  i.e "squash" refers to many different
                // things.
                else if ((output.indexOf(REFER) != -1) && 
                    Majel.isDisambiguating == false)
                {
                    // Redo the word lookup with the disambiguation flag set.
                    Majel.isDisambiguating = true;
                    output = this.doWordLookup(term, category);
                }
            }
            else if (category.equals(InputCategoryEnum.MUSIC) ||
                category.equals(InputCategoryEnum.MUSICCORRECTION))
            {
                // Are we searching for an artist, album, or song?
                String artistPath = String.format(
                    "//plist/dict/dict/dict/key[text()='Artist']/" + 
                    "following-sibling::string[1][contains(translate(., " 
                    + ALPHABETLOWERCASE + "," + ALPHABETUPPERCASE + ")," + "'" +
                    term.toUpperCase() + "')]");
                String albumPath = String.format(
                    "//plist/dict/dict/dict/key[text()='Album']/" + 
                    "following-sibling::string[1][contains(translate(., " 
                    + ALPHABETLOWERCASE + "," + ALPHABETUPPERCASE + ")," + "'" +
                    term.toUpperCase() + "')]");
                String songPath = String.format(
                    "//plist/dict/dict/dict/key[text()='Name']/" + 
                    "following-sibling::string[1][contains(translate(., " 
                    + ALPHABETLOWERCASE + "," + ALPHABETUPPERCASE + ")," + "'" +
                    term.toUpperCase() + "')]");
                XPathExpression artistExpr = xpath.compile(artistPath);
                XPathExpression albumExpr = xpath.compile(albumPath);
                XPathExpression songExpr = xpath.compile(songPath);
                if ((Boolean) artistExpr.evaluate(doc, XPathConstants.BOOLEAN))
                {
                    output = "Playing music by " + term;
                }
                else if ((Boolean) albumExpr.evaluate(doc, 
                    XPathConstants.BOOLEAN))
                {
                    output = "Playing music from the album \"" + term + 
                    "\"";
                }
                else if ((Boolean) songExpr.evaluate(doc, 
                    XPathConstants.BOOLEAN))
                {
                    output = "Playing the song \"" + term + "\"";
                }
                else
                {
                    output = "Unable to find any artist, album or song by " +
                    "that name in the music library.";
                }
            }
        }
        catch (ParserConfigurationException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (SAXException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (XPathExpressionException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return output;
    }
}
