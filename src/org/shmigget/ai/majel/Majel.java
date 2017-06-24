package org.shmigget.ai.majel;

import java.util.Scanner;
import org.shmigget.ai.majel.Categorizer.InputCategoryEnum;

/**
 * Majel is an AI agent who accepts input of six-types:
 * * Music request (play a song, album, or artist or pause music)
 * * Definition of a word or term
 * * Request for an encyclopedic description of a term
 * * Correction that the previous request was for music
 * * Correction for a definition
 * * Correction for a description
 * Note that in all of the above cases the term of interest must be wrapped in
 * quotation marks.  Hopefully this restriction will disappear once I'm able
 * to successfully train the OpenNLP Parser such that I can identify the 
 * object of the sentence.
 * 
 * After the request is serviced, Majel feeds the input into her set of training
 * data and regenerates the NLP model used for categorization.
 * 
 * @author Christopher A Dellario
 *
 */
public class Majel
{
    static boolean isDisambiguating = false;
    private final static String ENCODING = "UTF-8";
    private static final String MODEL_NAME = "resources/doccat_model.bin";
    private static boolean wasInputValid = true;

    
    /**
     * 
     * @return A String representing the text encoding used, e.g. UTF-8.
     */
    static String getEncoding()
    {
        return ENCODING;
    }
    
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        // Initialize a few things
        Categorizer categorizer = new Categorizer();
        Servicer servicer = new Servicer();
        Teacher teacher = new Teacher();
        
        while (true)
        {
            // 1) Start by grabbing the input (request or correction)
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();
            
            // 2) Categorize the input
            InputCategoryEnum category = 
                categorizer.categorizeInput(input, MODEL_NAME);
            
            // 3) Service the input
            String output = servicer.serviceRequest(category, input, teacher);
            
            // 4) Return the output to the user
            System.out.println(output);
            
            // 5) Learn from the input
            if (wasInputValid == true)
            {
                teacher.teach(input, category);
            }
        }
    }
    
    /**
     * 
     * @param output
     * @return
     */
    static void setWasInputValid(boolean b)
    {
        wasInputValid = b;
    }
}
