package be.ehb.azureOpenai;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

@SpringBootApplication
@RestController
public class Chatbot {

    private static final String speechKey = System.getenv("SPEECH_KEY");
    private static final String speechRegion = System.getenv("SPEECH_REGION");
    private static final String key = System.getenv("AZURE_OPENAI_KEY");
    private static final String endpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
    private static final String deploymentOrModelId = "chatgpt";
    private static String prompt;

    private static SpeechRecognizer speechRecognizer;
    private static SpeechSynthesizer speechSynthesizer;

    public static void main(String[] args) {
        SpringApplication.run(Chatbot.class, args);
    }

    @GetMapping("/")
    public String home() {
        return "Homepage";
    }

    @PostMapping("/startRecording")
    public String startRecording() {
        try {
            SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
            speechConfig.setSpeechRecognitionLanguage("en-US");

            recognizeFromMicrophone(speechConfig);

            return "Started recording";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to start recording";
        }
    }

    @PostMapping("/stopRecording")
    public String stopRecording() {
        if (speechRecognizer != null) {
            speechRecognizer.stopContinuousRecognitionAsync();
            speechRecognizer.close();
            speechRecognizer = null;

            return "Stopped recording";
        } else {
            return "Recording is not currently active";
        }
    }

    @PostMapping("/startSynthesizing")
    public String startSynthesizing() {
        try {
            String chatbotResponse = queryChatbot(prompt);
            textToSpeech(chatbotResponse);

            if (speechRecognizer != null) {
            speechRecognizer.stopContinuousRecognitionAsync();
            speechRecognizer.close();
            speechRecognizer = null;

            return "Started and stop synthesizing";
        } else {
            return "Started but didn't stop synthesizing";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to start and stop synthesizing";
        }
    }

    private void recognizeFromMicrophone(SpeechConfig speechConfig) throws InterruptedException, ExecutionException {
        AudioConfig audioConfig = AudioConfig.fromDefaultMicrophoneInput();
        speechRecognizer = new SpeechRecognizer(speechConfig, audioConfig);

        System.out.println("Speak into your microphone.");
        Future<SpeechRecognitionResult> task = speechRecognizer.recognizeOnceAsync();
        SpeechRecognitionResult speechRecognitionResult = task.get();

        if (speechRecognitionResult.getReason() == ResultReason.RecognizedSpeech) {
            prompt = speechRecognitionResult.getText();
            System.out.println("RECOGNIZED: Text=" + prompt);
        } else if (speechRecognitionResult.getReason() == ResultReason.NoMatch) {
            System.out.println("NOMATCH: Speech could not be recognized.");
        } else if (speechRecognitionResult.getReason() == ResultReason.Canceled) {
            CancellationDetails cancellation = CancellationDetails.fromResult(speechRecognitionResult);
            System.out.println("CANCELED: Reason=" + cancellation.getReason());

            if (cancellation.getReason() == CancellationReason.Error) {
                System.out.println("CANCELED: ErrorCode=" + cancellation.getErrorCode());
                System.out.println("CANCELED: ErrorDetails=" + cancellation.getErrorDetails());
                System.out.println("CANCELED: Did you set the speech resource key and region values?");
            }
        }
    }

    private static String queryChatbot(String question) {

        OpenAIClient client = new OpenAIClientBuilder()
        .credential(new AzureKeyCredential(key))
        .endpoint(endpoint)
        .buildClient();

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage(ChatRole.USER).setContent(question));

        ChatCompletions chatCompletions = client.getChatCompletions(deploymentOrModelId, new ChatCompletionsOptions(chatMessages));

        displayModelCreationInfo(chatCompletions.getId(), chatCompletions.getCreated());
        for (ChatChoice choice : chatCompletions.getChoices()) {
            ChatMessage message = choice.getMessage();
            System.out.printf("Index: %d, Chat Role: %s.%n", choice.getIndex(), message.getRole());
            System.out.println("Message:");
            System.out.println(message.getContent());
            return message.getContent();
        }
        return "error";
    }

    private static void displayModelCreationInfo(String modelId, long timestamp) {
        Instant instant = Instant.now();

        DateTimeFormatter formatter = DateTimeFormatter
            .ofLocalizedDateTime( FormatStyle.SHORT )
            .withLocale( Locale.FRANCE )
            .withZone( ZoneId.systemDefault() );
        String formattedDate = formatter.format(instant);

        System.out.printf("Model ID=%s is created at %s.%n", modelId, formattedDate);
    }

    private static void textToSpeech(String text) throws InterruptedException, ExecutionException {
        SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
        speechConfig.setSpeechSynthesisVoiceName("en-US-DavisNeural"); 
        speechSynthesizer = new SpeechSynthesizer(speechConfig);
        
        if (text.isEmpty())
        {
            return;
        }

        SpeechSynthesisResult speechSynthesisResult = speechSynthesizer.SpeakTextAsync(text).get();

        if (speechSynthesisResult.getReason() == ResultReason.SynthesizingAudioCompleted) {
            System.out.println("Speech synthesized to speaker for text [" + text + "]");
        }
        else if (speechSynthesisResult.getReason() == ResultReason.Canceled) {
            SpeechSynthesisCancellationDetails cancellation = SpeechSynthesisCancellationDetails.fromResult(speechSynthesisResult);
            System.out.println("CANCELED: Reason=" + cancellation.getReason());

            if (cancellation.getReason() == CancellationReason.Error) {
                System.out.println("CANCELED: ErrorCode=" + cancellation.getErrorCode());
                System.out.println("CANCELED: ErrorDetails=" + cancellation.getErrorDetails());
                System.out.println("CANCELED: Did you set the speech resource key and region values?");
            }
        }
    }
}
