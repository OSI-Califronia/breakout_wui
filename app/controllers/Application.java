package controllers;

import java.io.File;

import play.libs.F.Callback;
import play.libs.F.Callback0;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;

import com.google.gson.Gson;

import de.luma.breakout.communication.ObservableGame.GAME_STATE;
import de.luma.breakout.communication.ObservableGame.MENU_ITEM;
import de.luma.breakout.controller.GameController;
import de.luma.breakout.controller.IGameController;
import de.luma.breakout.controller.IGameController.PLAYER_INPUT;

public class Application extends Controller  {
    
	private static IGameController gameController;	
	
	static {
		getGameController();
	}
	
	
	private static IGameController getGameController() {
		if (gameController == null) {
			gameController = new GameController();			
			gameController.initialize();		
		}
		return gameController;
	}
	
	
		
	/**
	 * Returns the main page layout
	 */
    public static Result index() {
    	return ok(views.html.index.render());
    }
    
    /**
     * Returns content for #PlayGrid div
     */
    public static Result playGrid() {
   	
    	if (getGameController().getState() == GAME_STATE.RUNNING) {   // render playgrid
    		return ok(views.html.gamegrid.render(
    				getGameController().getGridSize().width,
    				getGameController().getGridSize().height,
    				HtmlHelper.getBricks(gameController), HtmlHelper.getBalls(gameController)));
    	} else if (getGameController().getState() == GAME_STATE.MENU_LEVEL_SEL) {
    		return getLevels();
    	} else {  // render menu
    		return ok(views.html.menu.render(HtmlHelper.getMenu(
    				gameController.getGameMenu().getMenuItems(),
    				gameController.getGameMenu().getTitle())));
    	}
    }
   
    private static Result getLevels() {   	    	
    	Gson gson = new Gson();
    	String json = gson.toJson(getGameController().getLevelList());

    	response().setContentType("Application.levellist");
 		return ok(json); 
    }
    
    public static Result loadLevel(String file) {
    	getGameController().loadLevel(new File(file));
    	getGameController().processMenuInput(MENU_ITEM.MNU_CONTINUE);
    	return ok();
    }
    
    
    /**
     * Processes a click on a menu button
     */
    public static Result selectmenu(String index) {
    	int itemIndex = Integer.valueOf(index);
    	getGameController().processMenuInput(MENU_ITEM.values()[itemIndex]);
    	
    	return index();
    }
    
    /**
     * Processes a key event on the play grid
     */
    public static Result gameInput(String key) {
    	switch (key) {
		case "escape":
			getGameController().processGameInput(PLAYER_INPUT.PAUSE);
			break;
		case "right":
			getGameController().processGameInput(PLAYER_INPUT.RIGHT);
			getGameController().processGameInput(PLAYER_INPUT.RIGHT);
			break;
		case "left":
			getGameController().processGameInput(PLAYER_INPUT.LEFT);
			getGameController().processGameInput(PLAYER_INPUT.LEFT);
			break;
		default:
			break;
		}
    	return ok();
    }
  
    
    
    // #################### WEBSOCKET ##########################
    
    public static Result socket_index() {
    	return ok(views.html.socket_index.render());
    }
    
    public static WebSocket<String> socket_connect() {
    	return new GameWebSocket(getGameController());
    }
    
}
