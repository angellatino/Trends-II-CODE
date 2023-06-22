package be.ehb.azureOpenai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;

public class Chatbot 
{
    public static void main( String[] args )
    {

        // OpenAIClient client = new OpenAIClientBuilder()
        // .credential(new NonAzureOpenAIKeyCredential("{openai-secret-key}"))
        // .buildClient();

        String key = System.getenv("AZURE_OPENAI_KEY");
        String endpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
        String deploymentOrModelId = "chatgpt";

        OpenAIClient client = new OpenAIClientBuilder()
        .credential(new AzureKeyCredential(key))
        .endpoint(endpoint)
        .buildClient();

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage(ChatRole.USER).setContent("Zou ik als jongere politiek actief moeten zijn?"));
        ChatCompletions chatCompletions = client.getChatCompletions(deploymentOrModelId, new ChatCompletionsOptions(chatMessages));

        displayModelCreationInfo(chatCompletions.getId(), chatCompletions.getCreated());
        for (ChatChoice choice : chatCompletions.getChoices()) {
            ChatMessage message = choice.getMessage();
            System.out.printf("Index: %d, Chat Role: %s.%n", choice.getIndex(), message.getRole());
            System.out.println("Message:");
            System.out.println(message.getContent());
        }

        // System.out.println();
        // CompletionsUsage usage = chatCompletions.getUsage();
        // System.out.printf("Usage: number of prompt token is %d, "
        //         + "number of completion token is %d, and number of total tokens in request and response is %d.%n",
        //     usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private static void displayModelCreationInfo(String modelId, long timestamp) {
        Instant instant = Instant.ofEpochSecond(timestamp);
        LocalDate creationDate = instant.atOffset(ZoneOffset.UTC).toLocalDate();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        String formattedDate = creationDate.format(formatter);

        System.out.printf("Model ID=%s is created at %s.%n", modelId, formattedDate);
    }
}