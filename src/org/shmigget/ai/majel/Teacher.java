package org.shmigget.ai.majel;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Scanner;

import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

import org.shmigget.ai.majel.Categorizer.InputCategoryEnum;

public class Teacher
{
    /**
     * Location of the training model file.
     */
    private static final String MODELFILELOCATION = 
        "resources/doccat_model.bin";
    
    /**
     * Location of the training file.
     */
    private static final String TRAININGFILELOCATION = 
        "resources/categorizer_training.txt";

    /**
     * Holds the contents of the training file.
     */
    private StringBuilder trainingSentences = null;
    
    // Constructor
    Teacher()
    {
        trainingSentences = new StringBuilder();
        String NL = System.getProperty("line.separator");
        Scanner scanner = null;
        try
        {
            scanner = new Scanner(new 
                FileInputStream(TRAININGFILELOCATION));
            while (scanner.hasNextLine())
            {
                this.trainingSentences.append(scanner.nextLine() + NL);
            }
        }
        catch (FileNotFoundException e)
        {
            System.out.println("Training file could not be found at " + 
                TRAININGFILELOCATION);
        }
        finally
        {
          scanner.close();
        }
    }
    
    /**
     * If the input was a request, add it to the top of the list of training
     * sentences.
     * If the input was a correction, reclassify the most recent request and
     * then add the correction to the top of the list.
     * 
     * @param input
     * @param category
     */
    void teach(String input, InputCategoryEnum category)
    {
        switch(category)
        {
            case MUSICCORRECTION:
            case DEFINITIONCORRECTION:
            case DESCRIPTIONCORRECTION:
                // We got a correction, so reclassify the most recent request.
                int mostRecentRequestPosition = 
                    this.findMostRecentRequestPosition();
                this.recategorizeMostRecentRequest(category, 
                    mostRecentRequestPosition);
                // And was this a correction of a correction as well?
                int mostRecentCorrectionPosition = 
                    this.findMostRecentCorrectionPosition();
                boolean isCorrectionOfCorrection = false;
                if (mostRecentCorrectionPosition > mostRecentRequestPosition)
                {
//System.out.println("CORRECTION OF A CORRECTION");
                    isCorrectionOfCorrection = true;
                }
                this.recategorizeMostRecentCorrection(category, 
                    mostRecentCorrectionPosition, isCorrectionOfCorrection);
                break;
        }    

        // Regardless of the category, now add the input to the set of training
        // sentences.
        String newTrainingSentence = category.getCategoryName() + " " + input +
            "\n";
        this.trainingSentences.append(newTrainingSentence);
        
        // Finally, write out the updated file and generate the new model.
        this.writeFile(TRAININGFILELOCATION, this.trainingSentences.toString());
        this.generateModel();
        
    }
    
    /**
     * Returns the position of the most recent correction within the set of
     * training data.
     * 
     * @return
     */
    private int findMostRecentCorrectionPosition()
    {
        int musicCorPos = this.trainingSentences.lastIndexOf(
            InputCategoryEnum.MUSICCORRECTION.getCategoryName());
        int defCorPos = this.trainingSentences.lastIndexOf(
            InputCategoryEnum.DEFINITIONCORRECTION.getCategoryName());
        int descCorPos = this.trainingSentences.lastIndexOf(
            InputCategoryEnum.DESCRIPTIONCORRECTION.getCategoryName());
        
        int maxMusicOrDef = Math.max(musicCorPos, defCorPos);
        return Math.max(maxMusicOrDef, descCorPos);
    }
    
    /**
     * Returns the position of the most recent request in the training data.
     * @return
     */
    private int findMostRecentRequestPosition()
    {
        int musicReqPos = this.trainingSentences.lastIndexOf(
            InputCategoryEnum.MUSIC.getCategoryName());
        int defReqPos = this.trainingSentences.lastIndexOf(
            InputCategoryEnum.DEFINITION.getCategoryName());
        int descReqPos = this.trainingSentences.lastIndexOf(
            InputCategoryEnum.DESCRIPTION.getCategoryName());
        
        int maxMusicOrDef = Math.max(musicReqPos, defReqPos);
        return Math.max(maxMusicOrDef, descReqPos);
    }
    
    /**
     * Generates the DocumentCategorizer model following the addition or 
     * modification of the training data set.
     */
    private void generateModel()
    {
        InputStream dataIn = null;
        try 
        {
            dataIn = new FileInputStream(TRAININGFILELOCATION);
            ObjectStream<String> lineStream =
                  new PlainTextByLineStream(dataIn, Majel.getEncoding());
            ObjectStream<DocumentSample> sampleStream = new 
                DocumentSampleStream(
                lineStream);
            
            // Suppress console output since the library is printing stuff out.
            PrintStream printStreamOriginal = System.out;
            System.setOut(new PrintStream(new OutputStream()
                {
                    public void write(int b) {}
                }));
            DoccatModel model = DocumentCategorizerME.train(
                "en", sampleStream, 1, 100);
            // No need to suppress anymore.
            System.setOut(printStreamOriginal);
            model.serialize(new FileOutputStream(MODELFILELOCATION));
        }
        catch (IOException e) 
        {
            // Failed to read or parse training data, training failed
            e.printStackTrace();
        }
        finally 
        {
            if (dataIn != null) 
            {
                try 
                {
                    dataIn.close();
                }
                catch (IOException e) 
                {
                    // Not an issue, training already finished.
                    // The exception should be logged and investigated
                    // if part of a production system.
                    e.printStackTrace();
                }
            }
        }        
    }
    
    /**
     * Returns the most recent request term.
     * @return
     */
    String getMostRecentRequestTerm()
    {
        int reqPos = this.findMostRecentRequestPosition();
        int firstQuotePos = this.trainingSentences.indexOf("\"", reqPos) + 1;
        int secondQuotePos = this.trainingSentences.indexOf("\"", 
            firstQuotePos + 1);
        
        String term = this.trainingSentences.substring(firstQuotePos, 
            secondQuotePos);
        return term;
    }
    
    /**
     * Returns the category of the most recent request.
     * 
     * @param reqPos
     * @return
     */
    private InputCategoryEnum getMostRecentRequestType(int reqPos)
    {
        String category = this.trainingSentences.substring(reqPos, 
            this.trainingSentences.indexOf(" ", reqPos));
        
        return InputCategoryEnum.getCategory(category);
    }
    
    /**
     * Modifies the training dataset to reflect the recategorization of either
     * the most recent request or correction.
     * 
     * @param category
     * @param mostRecentPosition
     * @param isCorrectionOfCorrection
     */
    private void recategorizeMostRecent(InputCategoryEnum category, 
        int mostRecentPosition, boolean isCorrectionOfCorrection)
    {
        int startPos = mostRecentPosition;
        InputCategoryEnum type = this.getMostRecentRequestType(startPos);

        int endPos = 0;
        
        if (isCorrectionOfCorrection == false)
        {
            switch (type)
            {
                case MUSIC:
                    endPos = InputCategoryEnum.MUSIC.getCategoryName().length();
                    break;
                case DEFINITION:
                    endPos = InputCategoryEnum.DEFINITION.getCategoryName().
                        length();
                    break;
                case DESCRIPTION:
                    endPos = InputCategoryEnum.DESCRIPTION.getCategoryName().
                        length();
                    break;
            }
        }
        else
        {
            switch (category)
            {
                case MUSICCORRECTION:
                    endPos = InputCategoryEnum.MUSICCORRECTION.
                        getCategoryName().length();
                    break;
                case DEFINITIONCORRECTION:
                    endPos = InputCategoryEnum.DEFINITIONCORRECTION.
                        getCategoryName().length();
                    break;
                case DESCRIPTIONCORRECTION:
                    endPos = InputCategoryEnum.DESCRIPTIONCORRECTION.
                        getCategoryName().length();
                    break;
            }            
        }
        
        String replacementCategory = null;
        
        switch (category)
        {
            case MUSICCORRECTION:
                replacementCategory = InputCategoryEnum.MUSIC.getCategoryName();
                break;
            case DEFINITIONCORRECTION:
                replacementCategory = InputCategoryEnum.DEFINITION.
                    getCategoryName();
                break;
            case DESCRIPTIONCORRECTION:
                replacementCategory = InputCategoryEnum.DESCRIPTION.
                    getCategoryName();
                break;
        }

        this.trainingSentences.replace(startPos, startPos + endPos, 
            replacementCategory);
    }
    
    /**
     * Recategorizes the most recent correction.
     * 
     * @param category
     * @param mostRecentCorrectionPosition
     * @param isCorrectionOfCorrection
     */
    private void recategorizeMostRecentCorrection(InputCategoryEnum category, 
        int mostRecentCorrectionPosition, boolean isCorrectionOfCorrection)
    {
        this.recategorizeMostRecent(category, mostRecentCorrectionPosition, 
            isCorrectionOfCorrection);
    }
    
    /**
     * Recategorizes the most recent request.
     * @param category
     * @param mostRecentRequestPosition
     */
    private void recategorizeMostRecentRequest(InputCategoryEnum category, 
        int mostRecentRequestPosition)
    {
        this.recategorizeMostRecent(category, mostRecentRequestPosition, 
            false);
    }
    
    /**
     * Writes out the modified training dataset.
     * @param fileLocation
     * @param contents
     */
    private void writeFile(String fileLocation, String contents)
    {
        Writer out = null;
        try
        {
            out = new OutputStreamWriter(new FileOutputStream(
                fileLocation));
            out.write(contents);
        }
        catch (FileNotFoundException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally 
        {
            try
            {
                out.close();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }        
}
