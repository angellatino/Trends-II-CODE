package be.ehb.azureOpenai;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.microsoft.cognitiveservices.speech.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

@SpringBootApplication
@RestController
public class Chatbot {

    private static final String speechKey = System.getenv("SPEECH_KEY");
    private static final String speechRegion = System.getenv("SPEECH_REGION");
    private static final String key = System.getenv("AZURE_OPENAI_KEY");
    private static final String endpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
    private static final String deploymentOrModelId = "chatgpt";

    private static final Logger logger = LoggerFactory.getLogger(Chatbot.class);

    private static String prompt;
    private static byte[] audioFile;
    private static SpeechRecognizer speechRecognizer;
    private static SpeechSynthesizer speechSynthesizer;

    private static CompletableFuture<String> recognitionTask;

    public static void main(String[] args) {
        SpringApplication.run(Chatbot.class, args);
    }

    @GetMapping("/logs")
    public String getLogs() {
        // Capture the logs in a ByteArrayOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        logger.info("Fetching logs...");
        logger.info("Additional log statements if needed");

        // Redirect System.out to the ByteArrayOutputStream
        PrintStream oldOut = System.out;
        System.setOut(ps);

        // Print the logs
        logger.info("Printing logs...");
        logger.warn("This is a warning log");
        logger.error("This is an error log");

        // Restore System.out
        System.out.flush();
        System.setOut(oldOut);

        // Get the logs from the ByteArrayOutputStream as a string
        String logs = baos.toString();

        // Return the logs as part of the API response
        return logs;
    }

    @PostMapping("/startRecording")
    public ResponseEntity<String> startRecording() {
        try {
            SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
            speechConfig.setSpeechRecognitionLanguage("en-US");

            Future<SpeechRecognitionResult> recognitionTask = recognizeFromMicrophone(speechConfig);

            // Wait for the recognition task to complete
            SpeechRecognitionResult speechRecognitionResult = recognitionTask.get();

            if (speechRecognitionResult.getReason() == ResultReason.RecognizedSpeech) {
                prompt = speechRecognitionResult.getText();

                // Close the speechRecognizer object
                if (speechRecognizer != null) {
                    speechRecognizer.stopContinuousRecognitionAsync();
                    speechRecognizer.close();
                    speechRecognizer = null;
                }

                return ResponseEntity.ok(prompt);
            } else {
                // Close the speechRecognizer object
                if (speechRecognizer != null) {
                    speechRecognizer.stopContinuousRecognitionAsync();
                    speechRecognizer.close();
                    speechRecognizer = null;
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } catch (Exception e) {
            logger.error("Failed to start recording", e);

            // Close the speechRecognizer object
            if (speechRecognizer != null) {
                speechRecognizer.stopContinuousRecognitionAsync();
                speechRecognizer.close();
                speechRecognizer = null;
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /*
    @PostMapping("/startRecording")
    public ResponseEntity<String> startRecording() {
        try {
            SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
            speechConfig.setSpeechRecognitionLanguage("en-US");

            SpeechRecognitionResult speechRecognitionResult = recognizeFromMicrophone(speechConfig);

            if (speechRecognitionResult.getReason() == ResultReason.RecognizedSpeech) {
                String prompt = speechRecognitionResult.getText();
                return ResponseEntity.ok(prompt);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } catch (Exception e) {
            logger.error("Failed to start recording", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private SpeechRecognitionResult recognizeFromMicrophone(SpeechConfig speechConfig) throws InterruptedException, ExecutionException {
        AudioConfig audioConfig = AudioConfig.fromDefaultMicrophoneInput();
        SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig);

        logger.info("Speak into your microphone.");

        Future<SpeechRecognitionResult> task = recognizer.recognizeOnceAsync();
        return task.get();
    }
     */

    @PostMapping("/startSynthesizing")
    public ResponseEntity<Map<String, Object>> startSynthesizing() {
        try {
            String chatbotResponse = queryChatbot(prompt);
            audioFile = textToSpeech(chatbotResponse);

            if (speechSynthesizer != null) {
                speechSynthesizer.close();
                speechSynthesizer = null;
            }

            // Create a custom response object
            Map<String, Object> responseObject = new HashMap<>();
            responseObject.put("audioFile", audioFile);
            responseObject.put("chatbotResponse", chatbotResponse);
            
            return ResponseEntity.ok(responseObject);
         } catch (Exception e) {
            logger.error("Failed to start and stop synthesizing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private Future<SpeechRecognitionResult> recognizeFromMicrophone(SpeechConfig speechConfig) {
        AudioConfig audioConfig = AudioConfig.fromDefaultMicrophoneInput();
        speechRecognizer = new SpeechRecognizer(speechConfig, audioConfig);

        logger.info("Speak into your microphone.");
        return speechRecognizer.recognizeOnceAsync();
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
            logger.info("Index: %d, Chat Role: %s.%n", choice.getIndex(), message.getRole());
            logger.info("Message:");
            logger.info(message.getContent());
            return message.getContent();
        }
        return "";
    }

    private static void displayModelCreationInfo(String modelId, long timestamp) {
        Instant instant = Instant.now();

        DateTimeFormatter formatter = DateTimeFormatter
            .ofLocalizedDateTime( FormatStyle.SHORT )
            .withLocale( Locale.FRANCE )
            .withZone( ZoneId.systemDefault() );
        String formattedDate = formatter.format(instant);

        logger.info("Model ID=%s is created at %s.%n", modelId, formattedDate);
    }

    private static byte[] textToSpeech(String text) throws InterruptedException, ExecutionException {
        SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
        speechConfig.setSpeechSynthesisVoiceName("en-US-DavisNeural"); 
        speechSynthesizer = new SpeechSynthesizer(speechConfig);
        
        if (text.isEmpty()) {
            return new byte[0];
        }

        SpeechSynthesisResult speechSynthesisResult = speechSynthesizer.SpeakTextAsync(text).get();

        if (speechSynthesisResult.getReason() == ResultReason.SynthesizingAudioCompleted) {
            logger.info("Speech synthesized to speaker for text [" + text + "]");
            return speechSynthesisResult.getAudioData();
        } else if (speechSynthesisResult.getReason() == ResultReason.Canceled) {
            SpeechSynthesisCancellationDetails cancellation = SpeechSynthesisCancellationDetails.fromResult(speechSynthesisResult);
            logger.info("CANCELED: Reason=" + cancellation.getReason());

            if (cancellation.getReason() == CancellationReason.Error) {
                logger.info("CANCELED: ErrorCode=" + cancellation.getErrorCode());
                logger.info("CANCELED: ErrorDetails=" + cancellation.getErrorDetails());
                logger.info("CANCELED: Did you set the speech resource key and region values?");
            }
        }
        return new byte[0];
    }
}
