package org.shmigget.ai.majel;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.util.InvalidFormatException;

/**
 * Classifies input into six categories
 * @author Christopher A Dellario
 *
 */
public class Categorizer
{
    /**
     * Enumeration for containing the input categories.
     * 
     * @author Christopher A Dellario
     *
     */
    enum InputCategoryEnum
    {
        MUSIC("MusicCategory"),
        DEFINITION("DefinitionCategory"),
        DESCRIPTION("DescriptionCategory"),
        MUSICCORRECTION("MusicCorrectionCategory"),
        DEFINITIONCORRECTION("DefinitionCorrectionCategory"),
        DESCRIPTIONCORRECTION("DescriptionCorrectionCategory");

        private final String category;
        
        InputCategoryEnum(String category)
        {
            this.category = category;
        }
        
        public static InputCategoryEnum getCategory(String category)
        {
            for (InputCategoryEnum e : InputCategoryEnum.values())
            {
                if (e.category.equals(category))
                {
                    return e;
                }
            }
            return null;
        }
        
        public String getCategoryName()
        {
            return this.category;
        }
    }    
    
    /**
     * Does the work of categorizing the input.
     * 
     * @param input
     * @param modelName
     * @return The InputCategoryEnum category value
     */
    InputCategoryEnum categorizeInput(String input, String modelName)
    {
        InputCategoryEnum category = null;
        
        if (this.isNormalCase(input) == true)
        {
            Majel.isDisambiguating = false;
            
            try
            {
                FileInputStream is = new FileInputStream(modelName);
                DoccatModel model = new DoccatModel(is);
                DocumentCategorizerME docCategorizer = 
                    new DocumentCategorizerME(model);
                double[] outcomes = docCategorizer.categorize(input);
                String categoryStr = docCategorizer.getBestCategory(outcomes);
                System.out.println("Category is " + categoryStr);
                category = InputCategoryEnum.getCategory(categoryStr);
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            catch (InvalidFormatException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        else
        {
            category = InputCategoryEnum.DESCRIPTION;
        }
        return category;
    }
    
    /**
     * Determins if the input string contains nothing but the term of interest.
     * 
     * @param input
     * @return
     */
    private boolean isInputAllTerm(String input)
    {
        boolean isAllTerm = false;
        
        if ((input.indexOf('"') == 0) && (input.indexOf('"', 1) == 
                (input.length() - 1)))
        {
            isAllTerm = true;
        }
        return isAllTerm;
    }
    
    /**
     * Answers whether the state is one of normal request servicing as opposed
     * to the special disambiguation case.
     * 
     * @param input
     * @return
     */
    private boolean isNormalCase(String input)
    {
        boolean isNormal = true;
        
        // The normal request case is when the user issues a request or 
        // correction.  But there's a special case where the previous 
        // description request gave multiple responses and so need to be 
        // disambiguated.  The user can reply from that case with a single term 
        // and we'll assume they want the description in that case.
        if (Majel.isDisambiguating == true && 
                this.isInputAllTerm(input) == true)
        {
            isNormal = false;
        }
        return isNormal;
    }
}
