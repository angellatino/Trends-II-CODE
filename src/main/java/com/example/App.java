package com.example;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        String apiKey = "YOUR_API_KEY_HERE";
        OpenAiService service = new OpenAiService(apiKey);
        
        CompletionRequest request = CompletionRequest.builder()
            .prompt("Wat is ErasmusHogeschool Brussel? Wees bondig!")
            .model("text-davinci-003")
            .maxTokens(300)
            .build();
        CompletionResult response = service.createCompletion(request); String generatedText = response.getChoices().get(0).getText(); System.out.println(generatedText);
    }
}
