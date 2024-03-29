package application.models;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * A task for the background thread to search for the term from Wikipedia
 * @author Tommy Shi and Justin Teo
 *
 */
public class WikiSearchTask extends Task<Void> {

	private String _term;
	private CreationListModel _creationListModel;
	private Scene _prevScene;
	private Stage _stage;
	
	public WikiSearchTask(String term, CreationListModel model, Scene prevScene, Stage stage) {
		_term = term;
		_creationListModel = model;
		_prevScene = prevScene;
		_stage = stage;
	}
	
	@Override
	protected Void call() throws Exception {
		// Start the search process
		BashCommands wiki = new BashCommands("wikit " + _term);
		wiki.startBashProcess();
		wiki.getProcess().waitFor();

		// get search output
		InputStream stdout = wiki.getProcess().getInputStream();
		BufferedReader stdoutBuffered = new BufferedReader(new InputStreamReader(stdout));
		String wikiOutput = stdoutBuffered.readLine(); //Read output of process and store in field

		// send back to GUI thread
		Platform.runLater(new CompleteWikiSearch(wikiOutput, _term, _creationListModel, _prevScene, _stage));

		return null;
	}

}
