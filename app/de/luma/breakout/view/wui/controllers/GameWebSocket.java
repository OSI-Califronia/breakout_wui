package de.luma.breakout.view.wui.controllers;

import java.io.File;

import play.api.templates.Html;
import play.libs.F.Callback;
import play.libs.F.Callback0;
import play.mvc.WebSocket;

import com.google.gson.Gson;

import de.luma.breakout.communication.GAME_STATE;
import de.luma.breakout.communication.IGameObserver;
import de.luma.breakout.communication.MENU_ITEM;
import de.luma.breakout.controller.IGameController;
import de.luma.breakout.controller.IGameController.PLAYER_INPUT;

/**
 * Manages a WebSocket connection to a client.
 * Receives events from webbrowser and send game content to the client.
 */
public class GameWebSocket extends WebSocket<String> implements IGameObserver{
	
	private Gson gson;
	private play.mvc.WebSocket.In<String> in;
	private play.mvc.WebSocket.Out<String> out;
		
	private IGameController gameController;
	
	public GameWebSocket(IGameController gameController) {
		super();
		this.gson = new Gson();
		this.gameController = gameController;
	}
	
	@Override
	public void updateRepaintPlayGrid() {	}  // not required
 
	@Override
	public void updateOnResize() { }  // not required
	
	
	/**
	 * Notifies client of changed game state (JSON-formatted)
	 */
	@Override
	public void updateGameState(GAME_STATE state) {
		out.write("STATE:" + state);
		
		// level selection Menu
		if (gameController.getState() == GAME_STATE.MENU_LEVEL_SEL) {			
			out.write("LEVEL:" + gson.toJson(gameController.getLevelList()));
		}
	}

	/**
	 * Sends menu items to client (JSON-formatted)
	 */
	@Override
	public void updateGameMenu(MENU_ITEM[] menuItems, String title) {	
		System.out.println("updateGameMenu");
	
		out.write("MENU:" + gson.toJson(HtmlHelper.getMenu(menuItems, title)));
	}

	/**
	 * Sends the rendered play grid to the client.  (HTML)
	 */
	@Override
	public void updateGameFrame() {
		if (gameController.getState() != GAME_STATE.RUNNING) {
			return;
		}
		
		Html playGrid = de.luma.breakout.view.wui.views.html.gamegrid.render(
				gameController.getGridSize().width,
				gameController.getGridSize().height,
				HtmlHelper.getBricks(gameController), 
				HtmlHelper.getBalls(gameController));
		
		out.write("GRID:" + playGrid.body());	
	}


	/**
	 * Handles incoming events from webbrowser
	 */
	@Override
	public void onReady(play.mvc.WebSocket.In<String> in, play.mvc.WebSocket.Out<String> out) {
		
		// handler for incoming messages
		in.onMessage(new Callback<String>() {
			@Override
			public void invoke(String event) throws Throwable {
				// process key Input
				if (event.startsWith("key:"))  {
					String key = event.substring(event.indexOf(":") +1);
					GameWebSocket.this.gameController.processGameInput(PLAYER_INPUT.valueOf(key));					
				}
				
				// process menu Input
				if (event.startsWith("menu:")) {
					Integer index =  Integer.valueOf(event.substring(event.indexOf(":") +1));
					GameWebSocket.this.gameController.processMenuInput(MENU_ITEM.values()[index]);
				}
				
				// process level select
				if (event.startsWith("level:")) {
					String file = event.substring(event.indexOf(":") +1);
					GameWebSocket.this.gameController.loadLevel(new File(file));
					GameWebSocket.this.gameController.processMenuInput(MENU_ITEM.MNU_CONTINUE);
				}
			}			
		});
		
		// handler when browser/tab is closed
		in.onClose(new Callback0() {
			@Override
			public void invoke() throws Throwable {
				GameWebSocket.this.gameController.removeObserver(GameWebSocket.this);
			}
		});
		
		this.out = out;
		this.in = in;
		
		// add this client to the observers of the running game
		GameWebSocket.this.gameController.addObserver(GameWebSocket.this);
		
		// Send the current state of the game to the client
		// so that the client can show a menu that appeared before the connection was established.
		initFirstFrame();
	}

	
	private void initFirstFrame() {
		if (this.gameController.getState() != GAME_STATE.RUNNING) {
			updateGameState(GAME_STATE.MENU_MAIN);
			updateGameMenu(gameController.getGameMenu().getMenuItems(), gameController.getGameMenu().getTitle());
		}
	}
	
	
}
