package it.polimi.ingsw.view.cli;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import it.polimi.ingsw.core.model.LeaderCard;
import it.polimi.ingsw.core.model.Resource;
import it.polimi.ingsw.net.client.Client;
import it.polimi.ingsw.net.msg.MessageType;
import it.polimi.ingsw.net.msg.RequestMsg;
import it.polimi.ingsw.net.msg.ResponseMsg;
import it.polimi.ingsw.view.UserInterface;
import it.polimi.ingsw.view.compact.CardCollector;
import it.polimi.ingsw.view.compact.CompactPlayer;

import java.io.FileNotFoundException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.UUID;

/**
 * Main class for CLI operations.
 */
public class Cli implements UserInterface {
    Client client;
    CompactPlayer mySelf;
    CardCollector cardCollector;
    FancyPrinter fancyPrinter;
    InputHandler inputHandler;
    Scanner scan = new Scanner(System.in);

    /**
     * Class constructor.
     * @param client the client socket used to send messages to the server.
     */
    public Cli(Client client) throws FileNotFoundException {
        this.client = client;
        cardCollector = new CardCollector();
        fancyPrinter = new FancyPrinter();
        inputHandler = new InputHandler();
    }

    @Override
    public void handleRequest(RequestMsg request){
        switch(request.getMessageType()){
            case REGISTRATION_MESSAGE:
            case WELCOME_MESSAGE:
            case JOIN_GAME:
            case NUMBER_OF_PLAYERS:
            case START_GAME:
                handleSimpleRequest(request);
                break;
            case WAIT_START_GAME:
                ackSimpleRequest(request);
                break;
            case GAME_MESSAGE:
                switch (request.getPayload().get("gameAction").getAsString()){
                    case "START_GAME_COMMAND":
                        handleSimpleRequest(request);
                        break;
                    case "WAIT_FOR_PLAYERS":
                    case "WAIT_START_GAME":
                    case "SHORT_UPDATE":
                        ackSimpleRequest(request);
                        break;
                    case "CHOOSE_START_LEADERS":
                        handleChooseStartLeaders(request);
                        break;
                    case "CHOOSE_START_RESOURCES":
                        handleChooseStartResources(request);
                        break;
                    case "INITIAL_UPDATE":
                        handleInitialUpdate(request);
                        break;
                }
                break;
            default:
                break;
        }
    }

    /**
     * Method used to handle "simple" requests from the server: a simple request consists in a message and an expected
     * response such as a number or a string, with no collateral effects on the client.
     * @param requestMsg the request sent by the server.
     */
    private void handleSimpleRequest(RequestMsg requestMsg) {
        System.out.println(requestMsg.getPayload().get("message").getAsString());
        JsonObject payload = InputHandler.getInput(requestMsg.getPayload().getAsJsonObject("expectedResponse"));
        if(requestMsg.getMessageType() == MessageType.GAME_MESSAGE)
            payload.addProperty("gameAction", requestMsg.getPayload().get("gameAction").getAsString());
        client.send(new ResponseMsg(requestMsg.getIdentifier(), requestMsg.getMessageType(), payload));
    }

    /**
     * Method used to handle "simple" requests from the server which need only an acknowledgment. These requests consist
     * of a simple text message with no collateral effects.
     * @param requestMsg the request sent by the server.
     */
    private void ackSimpleRequest(RequestMsg requestMsg) {
        System.out.println(requestMsg.getPayload().get("message").getAsString());
        if(requestMsg.getMessageType() == MessageType.GAME_MESSAGE){
            JsonObject payload = new JsonObject();
            payload.addProperty("gameAction", requestMsg.getPayload().get("gameAction").getAsString());
            client.send(new ResponseMsg(requestMsg.getIdentifier(), requestMsg.getMessageType(), payload));
        } else {
            client.send(new ResponseMsg(requestMsg.getIdentifier(), requestMsg.getMessageType(), null));
        }
    }

    private void handleChooseStartLeaders(RequestMsg ms){
        LeaderCard lc;
        int x, y, j=0, k=0;
        int[] discardedLeaders = new int[2];
        int[] leaders = new int[2];
        int playerID = ms.getPayload().get("playerID").getAsInt();
        String playerName = ms.getPayload().get("playerName").getAsString();
        mySelf = new CompactPlayer(playerID, playerName);

        Gson gson = new Gson();
        String json = ms.getPayload().get("leaderCards").getAsString();
        Type collectionType = new TypeToken<int[]>(){}.getType();
        int[] leaderCards = gson.fromJson(json, collectionType);

        System.out.println("\nGame is started!!");
        System.out.println("You're player"+ms.getPayload().get("playerOrder").getAsInt());

        for (int i = 0; i < 4; i++) {
            lc = cardCollector.getLeaderCard(leaderCards[i]);
            System.out.println("."+(i+1)+"\n");
            //fancyPrinter.printLeaderCard(lc);
        }
        //serve un controllo, magari cambiamo su inputHandler
        System.out.println("Choose a card to discard\n");
        x = scan.nextInt();
        System.out.println("Choose another card to discard\n");
        y = scan.nextInt();

        for (int i = 0; i < 4; i++) {
            if (i == (x - 1) || i == (y - 1)) {
                discardedLeaders[j] = leaderCards[i];
                j++;
            } else {
                leaders[k] = leaderCards[i];
                k++;
            }
        }

        mySelf.getCompactBoard().setLeaderCards(leaders);

        JsonObject payload = new JsonObject();
        payload.addProperty("gameAction", "CHOOSE_START_LEADERS");
        payload.addProperty("playerID", mySelf.getPlayerID());
        json = gson.toJson(discardedLeaders); //forse sarebbe meglio trasformarlo in array
        payload.addProperty("discardedLeaders", json);

        //cos'è UUID
        client.send(new ResponseMsg(UUID.randomUUID(), MessageType.GAME_MESSAGE, payload));
    }

    private void handleChooseStartResources(RequestMsg ms){
        int x, y, n;
        Resource[] placed1 = new Resource[10];

        Resource a, b;
        if(ms.getPayload().get("resources").getAsInt() == 0)
            System.out.println("\nWait for the other players to finish their initial turn");
        else{
            x = ms.getPayload().get("resources").getAsInt();
            y = ms.getPayload().get("faithPoints").getAsInt();
            System.out.println("\nYou are entitled to "+x+" resources ");
            if(y!=0)
                System.out.println("and "+y+" faith points");

            System.out.println("\nChoose "+x+" resources:");
            System.out.println("\n1. COIN");
            System.out.println("\n2. SHIELD");
            System.out.println("\n3. STONE");
            System.out.println("\n4. SERVANT");

            n = scan.nextInt();
            a = Resource.values()[n-1];
            if(x == 2){
                n = scan.nextInt();
                b = Resource.values()[n-1];
                if(a.equals(b)){
                    placed1[1] = a;
                    placed1[2] = b;
                }else{
                    placed1[0] = a;
                    placed1[1] = b;
                }
            }else{
                placed1[0] = a;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("gameAction", "CHOOSE_START_RESOURCES");
            payload.addProperty("playerID", mySelf.getPlayerID());
            Gson gson = new Gson();
            String json = gson.toJson(new ArrayList<>(Arrays.asList(placed1))); //forse sarebbe meglio trasformarlo in array
            payload.addProperty("placed", json);

            client.send(new ResponseMsg(UUID.randomUUID(), MessageType.GAME_MESSAGE, payload));
        }
    }

    private void handleInitialUpdate(RequestMsg ms){
        System.out.println("\nQUA FARò UPDATE");
    }
}
