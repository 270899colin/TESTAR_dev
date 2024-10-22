package org.testar.action.priorization.llm;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testar.IActionSelector;
import org.testar.monkey.ConfigTags;
import org.testar.monkey.Main;
import org.testar.monkey.alayer.*;
import org.testar.monkey.alayer.actions.CompoundAction;
import org.testar.monkey.alayer.actions.NOP;
import org.testar.monkey.alayer.actions.PasteText;
import org.testar.monkey.alayer.actions.Type;
import org.testar.monkey.alayer.exceptions.NoSuchTagException;
import org.testar.monkey.alayer.webdriver.enums.WdTags;
import org.testar.settings.Settings;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Protocol for selecting actions using a large language model (LLM).
 * The LLM picks an action based on a test goal and given list of actions in the form of a prompt.
 * The selector communicates with the LLM using a Web API that complies with the OpenAI API used by OpenAI and LMStudio.
 * https://platform.openai.com/docs/overview
 */
public class LlmActionSelector implements IActionSelector {
    protected static final Logger logger = LogManager.getLogger();

    private final String platform;
    private final String host;
    private final String port;
    private final String testGoal;
    private final String fewshotFile;
    private final String appName;

    private ActionHistory actionHistory = new ActionHistory(5);
    private LlmConversation conversation;
    private int tokens_used;

    private Gson gson = new Gson();

    /**
     * Creates a new LlmActionSelector.
     * @param settings with contains:
     * 1. LlmHostAddress for the host of the OpenAI compatible LLM API. Ex: http://127.0.0.1.
     * 2. LlmHostPort for the port of the API.
     * 3. LlmTestGoalDescription for the objective of the test. Ex: Log in with username john and password demo.
     * 4. LlmFewshotFile for the fewshot file that contains the prompt instructions.
     * 5. ApplicationName for the name of the SUT.
     */
    public LlmActionSelector(Settings settings) {
        this.platform = settings.get(ConfigTags.LlmPlatform);
        this.host = settings.get(ConfigTags.LlmHostAddress);
        this.port = settings.get(ConfigTags.LlmHostPort);
        this.testGoal = settings.get(ConfigTags.LlmTestGoalDescription);
        this.fewshotFile = settings.get(ConfigTags.LlmFewshotFile);
        this.appName = settings.get(ConfigTags.ApplicationName);

        conversation = LlmFactory.createLlmConversation(this.platform);
        conversation.initConversation(this.fewshotFile);
    }

    @Override
    public Action selectAction(State state, Set<Action> actions) {
        return selectActionWithLlm(state, actions);
    }

    /**
     * Selects an action to take using the LLM:
     * 1. The prompt is generated.
     * 2. The prompt is sent to the LLM.
     * 3. The response from the LLM is parsed.
     * TODO: Trim conversation when exceeding token limit?
     * @param state The current state of the SUT.
     * @param actions Set of actions in the current state.
     * @return The action to execute or null if failed.
     */
    private Action selectActionWithLlm(State state, Set<Action> actions) {
        String prompt = generatePrompt(actions);
        logger.log(Level.DEBUG, "Generated prompt: " + prompt);
        conversation.addMessage("user", prompt);

        String conversationJson = gson.toJson(conversation);
        String llmResponse = getResponseFromLlm(conversationJson);
        LlmParseResult llmParseResult = parseLlmResponse(actions, llmResponse);

        switch(llmParseResult.getParseResult()) {
            case SUCCESS: {
                Action actionToTake = llmParseResult.getActionToExecute();

                logger.log(Level.DEBUG, "Selected action: " + actionToTake.toShortString());

                conversation.addMessage("user", llmResponse);
                actionHistory.addToHistory(actionToTake);

                return actionToTake;
            }
            case SUCCESS_FINISH:  {
                // Terminate test.
                return null;
            }
            // Failures return no operation (NOP) actions to prevent crashing.
            // We do not add these to the action history.
            case OUT_OF_RANGE: {
                conversation.addMessage("user", "The actionId provided was invalid.");
                return new NOP();
            }
            case PARSE_FAILED: {
                conversation.addMessage("user", 
                        "The output you provided was not formatted correctly. "
                        + "Please use the following format: \n\n"
                        + "{\n"
                        + "\"actionId\": \"ACT0K4\",\n"
                        + "\"input\": \"Text\"\n"
                        + "}");
                return new NOP();
            }
            default: {
                logger.log(Level.ERROR, "ParseResult was null, this should never happen!");
                return new NOP();
            }
        }
    }

    /**
     * Generates the prompt to be sent to the LLM based on the set of actions in the current state.
     * The prompt consists of the application name, test goal, available actions, and action history if available.
     * TODO: Add information about the current state, such as the page title.
     * @param actions Set of actions in the current state.
     * @return The generated prompt.
     */
    private String generatePrompt(Set<Action> actions) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("We are testing the \"%s\" web application. ", appName));
        builder.append(String.format("The objective of the test is: %s. ", testGoal));
        builder.append("The following actions are available: ");

        for (Action action : actions) {
            try {
                Widget widget = action.get(Tags.OriginWidget);
                String type = action.get(Tags.Role).name();
                String actionId = action.get(Tags.ConcreteID, "Unknown ActionId");
                String description = widget.get(Tags.Desc, "No description");

                // Depending on the action, format into something the LLM is more likely to understand.
                switch(type) {
                    case "ClickTypeInto":
                        // Differentiate between types of input fields. Example: password -> Password Field
                        String fieldType = StringUtils.capitalize(widget.get(WdTags.WebType, "text"));
                        builder.append(String.format("%s: Type in %sField '%s'", actionId, fieldType, description));
                        break;
                    case "LeftClickAt":
                        builder.append(String.format("%s: Click on '%s'", actionId, description));
                        break;
                    default:
                        logger.log(Level.WARN, "Unsupported action type for LLM action selection: " + type);
                        break;
                }

            } catch(NoSuchTagException e) {
                // This usually happens when OriginWidget is unknown, so we skip these.
                logger.log(Level.WARN, "Action is missing critical tags, skipping.");
            }

            builder.append(", ");
        }

        builder.append(". ");

        if(!actionHistory.getActions().isEmpty()) {
            builder.append(actionHistory.toString());
        }

        builder.append("Which action should be executed to accomplish the test goal?");

        return builder.toString();
    }

    /**
     * Sends a POST request to the LLM's API and returns the response as a string.
     * @param requestBody Request body of the POST request.
     * @return Response content or null if failed.
     */
    private String getResponseFromLlm(String requestBody) {
        String testarVer = Main.TESTAR_VERSION.substring(0, Main.TESTAR_VERSION.indexOf(" "));
        URI uri = URI.create(replaceApiKeyPlaceholder(this.host + ":" + this.port));

        logger.log(Level.DEBUG, "Using endpoint: " + uri);
        logger.log(Level.DEBUG, "Request Body: " + requestBody);

        try {
            URL url = uri.toURL();
            HttpURLConnection con = (HttpURLConnection)url.openConnection();

            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "testar/" + testarVer);
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setConnectTimeout(10000);

            try(OutputStream os = con.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if(con.getResponseCode() == 200) {
                try(BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine = null;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    LlmResponse llmResponse = LlmFactory.createResponse(this.platform, response);
                    this.tokens_used = llmResponse.getUsageTokens();
                    logger.log(Level.INFO, String.format("LLM tokens_used: [%s]", this.tokens_used));

                    String responseContent = llmResponse.getResponse();
                    // From testing, response often includes newlines and spaces at the end.
                    // We strip this here to so we can parse the result easier.
                    responseContent = responseContent.replace("\n", "").replace("\r", "");
                    responseContent = responseContent.replaceFirst("\\s++$", "");

                    logger.log(Level.INFO, String.format("LLM Response: [%s]", responseContent));

                    return responseContent;
                }
            } else {
                // If response is not 200 OK, debug the error message
                try(BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String responseLine = null;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }

                    logger.log(Level.ERROR, String.format("LLM error code %d response: %s", con.getResponseCode(), errorResponse));

                    throw new Exception("Server returned " + con.getResponseCode() + " status code.");
                }
            }
        } catch(Exception e) {
            logger.log(Level.ERROR, "Unable to communicate with the LLM.");
            e.printStackTrace();
            return null;
        }
    }

    private String replaceApiKeyPlaceholder(String url) {
        String apiKeyPlaceholder = extractPlaceholder(url);

        if (apiKeyPlaceholder.isEmpty()) {
            // Return the original URL if no placeholder is found
            return url;
        }

        String apiKey = System.getenv(apiKeyPlaceholder);
        if (apiKey == null) {
            // Return the original URL if the API key is not found in the system
            return url;
        }

        // Return the final URL with the placeholder replaced by the actual API key
        return url.replace("%" + apiKeyPlaceholder + "%", apiKey);
    }

    private String extractPlaceholder(String str) {
        int start = str.indexOf('%') + 1;
        int end = str.indexOf('%', start);

        if (start == 0 || end == -1) {
            // No valid placeholder found, return empty string
            return "";
        }

        // Return the placeholder between the '%' symbols
        return str.substring(start, end);
    }

    /**
     * Parses the response sent by the LLM and selects an action if the response was valid.
     * @param actions Set of actions in the current state.
     * @param responseContent The response of the LLM in plaintext.
     * @return LlmParseResult containing the result of the parse and the action to execute if parsing was successful.
     */
    private LlmParseResult parseLlmResponse(Set<Action> actions, String responseContent) {
        try {
            LlmSelection selection = gson.fromJson(responseContent, LlmSelection.class);

            // actionId 'complete' is used by the LLM when the LLM thinks the test objective was accomplished.
            // This will terminate the test in the default LLM protocol.
            if(selection.getActionId().toLowerCase().contains("complete")) {
                return new LlmParseResult(null, LlmParseResult.ParseResult.SUCCESS_FINISH);
            }

            Action selectedAction = getActionByIdentifier(actions, selection.getActionId());

            // If the selectedAction is a NOP action at this stage, parsing has likely failed.
            if(selectedAction instanceof NOP) {
                logger.log(Level.ERROR, "Action ConcreteID not found, parsing LLM response has likely failed!: " + responseContent);
                return new LlmParseResult(null, LlmParseResult.ParseResult.PARSE_FAILED);
            }

            String inputText = selection.getInput();

            setCompoundActionInputText(selectedAction, inputText);

            return new LlmParseResult(selectedAction, LlmParseResult.ParseResult.SUCCESS);

        } catch(JsonParseException e) {
            logger.log(Level.ERROR, "Unable to parse response from LLM to JSON: " + responseContent);
            return new LlmParseResult(null, LlmParseResult.ParseResult.PARSE_FAILED);
        }
    }

    private Action getActionByIdentifier(Set<Action> actions, String actionId) {
        for(Action action : actions) {
            if(action.get(Tags.ConcreteID, "").equals(actionId)) {
                return action;
            }
        }
        return new NOP();
    }

    /**
     * Sets TESTAR input text of compound Type and PasteText actions.
     * @param action CompoundAction to change.
     * @param input The characters to enter into the input field. Can be left empty if not applicable.
     * @return if input text changed.
     */
    private boolean setCompoundActionInputText(Action action, String inputText) {
        //TODO: Create single actions in protocol so this is not necessary?
        if(action instanceof CompoundAction) {
            for(Action innerAction : ((CompoundAction)action).getActions()) {

                if(innerAction instanceof Type) {
                    ((Type)innerAction).set(Tags.InputText, inputText);
                    action.set(Tags.Desc, "Type '" + ((Type)innerAction).get(Tags.InputText) 
                            + "' into '" + action.get(Tags.OriginWidget).get(Tags.Desc, "<no description>" + "'"));
                    return true;
                }

                if(innerAction instanceof PasteText) {
                    ((PasteText)innerAction).set(Tags.InputText, inputText);
                    action.set(Tags.Desc, "PasteText '" + ((PasteText)innerAction).get(Tags.InputText) 
                            + "' into '" + action.get(Tags.OriginWidget).get(Tags.Desc, "<no description>" + "'"));
                    return true;
                }
            }
        }

        return false;
    }
}
