package be.ehb.openai;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import com.azure.core.exception.HttpResponseException;
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

/**
 * Hello world!
 *
 */
public class App 
{
    private static final String speechKey = System.getenv("SPEECH_KEY");
    private static final String speechRegion = System.getenv("SPEECH_REGION");
    private static final String apiKey = "YOUR_API_KEY_HERE";
    private static final String song = """
                        Oh, I wonder how you move
                        Your hundred little legs
                        I've never seen
                        Them spin with such a grace
                        Your point in each and every step
                        It's like a thousand of the times
                        We could've changed
                        But we're cemented in place

                        Give me ataraxia

                        The air is hardening around us
                        And it's making me shake
                        When thinking of inching close
                        To your face
                        We're strangers in the excess
                        We're not like the others here
                        So won't you stay the night, dear,
                        And tell me I belong

                        Give me ataraxia
                        It's ataraxia

                        'Cause it fills
                        You fill me a little
                        Then straight through the bottom
                        We're all faking something
                        And you fill
                        You fill me a little
                        Then straight through the bottom
                        We promised we'd leave to live
                        We promised we'd leave to live
                        (the others, the others, the others)

                        Oh, I envy how you move
                        Those hundred little legs
                        I've never been
                        As fine without an aim
                        So won't you stay the night, dear,
                        And tell me I belong

                        Give me ataraxia

                        'Cause you fill
                        You fill me a little
                        Then straight to the bottom
                        Did you stay to prove you could
                        (the others, the others, the others)
                      """;

    private static SpeechRecognizer speechRecognizer;

    public static void main( String[] args ) throws InterruptedException, ExecutionException {
        SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
        speechConfig.setSpeechRecognitionLanguage("nl-NL");

        recognizeFromMicrophone(speechConfig);
    }

    public static void recognizeFromMicrophone(SpeechConfig speechConfig) throws InterruptedException, ExecutionException {
        AudioConfig audioConfig = AudioConfig.fromDefaultMicrophoneInput();
        speechRecognizer = new SpeechRecognizer(speechConfig, audioConfig);

        System.out.println("Speak into your microphone.");
        Future<SpeechRecognitionResult> task = speechRecognizer.recognizeOnceAsync();
        SpeechRecognitionResult speechRecognitionResult = task.get();

        if (speechRecognitionResult.getReason() == ResultReason.RecognizedSpeech) {
            String text = speechRecognitionResult.getText();
            System.out.println("RECOGNIZED: Text=" + text);

            if (isStopCommand(text)) {
                System.exit(0);
            }
            
            String answer = queryChatbot(song);
            textToSpeech(answer);
        }
        else if (speechRecognitionResult.getReason() == ResultReason.NoMatch) {
            System.out.println("NOMATCH: Speech could not be recognized.");
        }
        else if (speechRecognitionResult.getReason() == ResultReason.Canceled) {
            CancellationDetails cancellation = CancellationDetails.fromResult(speechRecognitionResult);
            System.out.println("CANCELED: Reason=" + cancellation.getReason());

            if (cancellation.getReason() == CancellationReason.Error) {
                System.out.println("CANCELED: ErrorCode=" + cancellation.getErrorCode());
                System.out.println("CANCELED: ErrorDetails=" + cancellation.getErrorDetails());
                System.out.println("CANCELED: Did you set the speech resource key and region values?");
            }
        }

        recognizeFromMicrophone(speechConfig);
    }

    public static boolean isStopCommand(String text) {
        return text.equalsIgnoreCase("stop.");
    }

    private static String queryChatbot(String lyrics) {

        OpenAiService service = new OpenAiService(apiKey);

        StringBuilder songBuilder = new StringBuilder();
        songBuilder.append(lyrics);
        
        try {
            CompletionRequest request = CompletionRequest.builder()
                .prompt("Sing the following for me: " + lyrics)
                .model("text-davinci-003")
                .maxTokens(300)
                .build();
            CompletionResult response = service.createCompletion(request); String generatedText = response.getChoices().get(0).getText();
            return generatedText;
        } catch (HttpResponseException e) {
        // Handle the error related to content management policy and filtering
            if (e.getMessage().contains("content management policy") || e.getMessage().contains("content filtering")) {
                System.err.println("Error: Content management policy or filtering issue occurred.");
                // Optionally, you can log the error or perform any necessary cleanup operations

                // Terminate the program
                System.exit(1);
            }

            return "";
        }
    }

    private static void textToSpeech(String text) throws InterruptedException, ExecutionException {
        SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);

        speechConfig.setSpeechSynthesisVoiceName("nl-BE-ArnaudNeural"); 

        SpeechSynthesizer speechSynthesizer = new SpeechSynthesizer(speechConfig);
        
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