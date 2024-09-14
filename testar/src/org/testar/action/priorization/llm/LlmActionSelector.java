package org.testar.action.priorization.llm;

import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testar.IActionSelector;
import org.testar.monkey.Main;
import org.testar.monkey.alayer.Action;
import org.testar.monkey.alayer.State;
import org.testar.monkey.alayer.Tags;
import org.testar.monkey.alayer.Widget;
import org.testar.monkey.alayer.actions.WdRemoteTypeAction;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;

public class LlmActionSelector implements IActionSelector {
    protected static final Logger logger = LogManager.getLogger();

    // TODO: Make configurable in GUI?
    private final String testGoal;
    private final String host;
    private final int port ;

    private ActionHistory actionHistory = new ActionHistory(5);
    private LlmConversation conversation;

    private Gson gson = new Gson();

    public LlmActionSelector(String testGoal) {
        this.testGoal = testGoal;

        // Use defaults
        this.host = "http://127.0.0.1";
        this.port = 1234;

        initConversation();
    }

    public LlmActionSelector(String testGoal, String host, int port) {
        this.testGoal = testGoal;
        this.host = host;
        this.port = port;

        initConversation();
    }

    private void initConversation() {
        conversation = new LlmConversation();

        // TODO: Make configurable
        try {
            conversation.addMessage("system", getTextResource("prompts/fewshot/llama_prompt1.txt"));
            conversation.addMessage("user", getTextResource("prompts/fewshot/llama_prompt2.txt"));
            conversation.addMessage("assistant", getTextResource("prompts/fewshot/llama_prompt3.txt"));
            conversation.addMessage("user", getTextResource("prompts/fewshot/llama_prompt4.txt"));
            conversation.addMessage("assistant", getTextResource("prompts/fewshot/llama_prompt5.txt"));
            conversation.addMessage("user", getTextResource("prompts/fewshot/llama_prompt6.txt"));
            conversation.addMessage("assistant", getTextResource("prompts/fewshot/llama_prompt7.txt"));
        } catch(Exception e) {
            logger.log(Level.ERROR, "Failed to initialize conversation, LLM quality may be degraded.");
        }
    }

    private String getTextResource(String resourceLocation) throws Exception {
        ClassLoader classLoader = LlmActionSelector.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(resourceLocation);

        if (inputStream != null) {
            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                return stringBuilder.toString();
            } catch (IOException e) {
                logger.log(Level.ERROR, "Unable to read resource " + resourceLocation);
                e.printStackTrace();
            }

        } else {
            logger.log(Level.ERROR, "Unable to load resource " + resourceLocation);
        }
        
        throw new Exception("Failed to load text resource, double check the resource location.");
    }

    private Action selectActionWithLlm(State state, Set<Action> actions) {
        // For debugging
        logger.log(Level.DEBUG, "Available actions: ");
        for (Action action : actions) {
            logger.log(Level.DEBUG, action.toShortString());
        }

        String prompt = generatePrompt(actions);
        logger.log(Level.DEBUG, "Generated prompt: " + prompt);
        conversation.addMessage("user", prompt);

        Action actionToTake = getVerdictFromLlm(new ArrayList<>(actions));
        logger.log(Level.DEBUG, "Selected action: " + actionToTake.toShortString());

        // Remove message to prevent hitting token limit, message will be regenerated each time.
        conversation.getMessages().remove(conversation.getMessages().size() - 1);
        actionHistory.addToHistory(actionToTake, "");

        return actionToTake;
    }

    private Action getVerdictFromLlm(ArrayList<Action> actions) {
        String testarVer = Main.TESTAR_VERSION.substring(0, Main.TESTAR_VERSION.indexOf(" "));
        URI uri = URI.create(this.host + ":" + this.port + "/v1/chat/completions");

        logger.log(Level.DEBUG, "Using endpoint: " + uri);

        String conversationJson = gson.toJson(conversation);

        try {
            URL url = new URL(this.host + ":" + this.port + "/v1/chat/completions");
            HttpURLConnection con = (HttpURLConnection)url.openConnection();

            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "testar/" + testarVer);
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setConnectTimeout(10000);

            try(OutputStream os = con.getOutputStream()) {
                byte[] input = conversationJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            try(BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                LlmResponse modelResponse = gson.fromJson(response.toString(), LlmResponse.class);

                String responseContent = modelResponse.getChoices().get(0).getMessage().getContent();

                if(con.getResponseCode() == 200) {
                    if(StringUtils.isNumeric(responseContent)) {
                        int actionToTake = Integer.parseInt(responseContent);
                        if(actionToTake >= actions.size()) {
                            return actions.get(actionToTake);
                        } else {
                            throw new ArrayIndexOutOfBoundsException("Index requested by LLM is out of bounds.");
                        }
                    } else {
                        String[] responseParts = responseContent.split(",");
                        if(responseParts.length == 2) {
                            if(StringUtils.isNumeric(responseContent)) {
                                int actionToTake = Integer.parseInt(responseParts[0]);
                                String parameters = responseParts[1];
                                return setActionParameters(actions.get(actionToTake), parameters);
                            } else {
                                throw new Exception("LLM output is invalid: " + responseContent);
                            }
                        } else {
                            throw new Exception("LLM output is invalid: " + responseContent);
                        }
                    }

                } else {
                    throw new Exception("Server returned " + con.getResponseCode() + " status code.");
                }
            }
        } catch(Exception e) {
            logger.log(Level.ERROR, "Unable to select action with the LLM");
            e.printStackTrace();
            return null;
        }
    }

    private Action setActionParameters(Action action, String parameters) {
        if(action instanceof WdRemoteTypeAction) {
            ((WdRemoteTypeAction) action).setKeys(parameters);
            return action;
        }

        return action;
    }

    private String generatePrompt(Set<Action> actions) {
        StringBuilder builder = new StringBuilder();
        builder.append("Objective: ").append(testGoal).append(". ");
        builder.append("Available actions: ");

        int i = 0;
        for (Action action : actions) {
            Widget widget = action.get(Tags.OriginWidget);

            builder.append(", ");

            String type = action.get(Tags.Role).name();
            String description = widget.get(Tags.Desc, "No description");

            builder.append(String.format("(%d,%s,%s)", i, type, description));
            i++;
        }
        builder.append(". ");
        builder.append(actionHistory.toString());

        return builder.toString();
    }

    @Override
    public Action selectAction(State state, Set<Action> actions) {
        return selectActionWithLlm(state, actions);
    }
}
