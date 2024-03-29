package application.controllers;

import application.models.BashCommands;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A controller class for the preview audio scene
 * @author Tommy Shi and Justin Teo
 *
 */
public class PreviewController extends Controller {

	@FXML private TextArea _previewTextArea;
	@FXML private ComboBox<String> _choiceOfVoice;
	@FXML private ComboBox<String> _choiceOfSpeed;
	@FXML private Button _cancelButton;
	@FXML private Button _playStopButton;
	@FXML private Button _saveButton;

	// set up the voice keys and values
	private static Map<String, String> VOICES_RECORDS = new HashMap<>(Map.of("Male Voice", "akl_nz_jdt_diphone",
			"Female Voice", "akl_nz_cw_cg_cg",
			"Default Machine Voice", "kal_diphone"
	));

	private CreationSceneController _controller;
	private String _selectedText; // original selected text
	private String _audioText; // selected text which punctuation has been removed
	private List<Integer> _count;
	private Stage _window;
	private Button _combineButton;
	private MediaPlayer _mediaPlayer;

	/**
	 * Get user choice of voice and speed.
	 * Enable play button when voice has been selected.
	 */
	@FXML
	private void onVoiceOptionChanged() {
		String voice = _choiceOfVoice.getSelectionModel().getSelectedItem();
		String speed = _choiceOfSpeed.getSelectionModel().getSelectedItem();
		if (voice != null && speed != null && !speed.isEmpty() && !voice.isEmpty()) {
			_playStopButton.setDisable(false);
			_saveButton.setDisable(true);
		}
	}

	/**
	 * Enable play button when speed has changed
	 */
	@FXML
	private void onSpeedChanged() {
		String voice = _choiceOfVoice.getSelectionModel().getSelectedItem();
		String speed = _choiceOfSpeed.getSelectionModel().getSelectedItem();
		if (voice != null && speed != null && !speed.isEmpty() && !voice.isEmpty()) {
			_playStopButton.setDisable(false);
		}
	}

	/**
	 * Cancel the preview process
	 * @throws InterruptedException
	 */
	@FXML
	private void onCancelButtonPressed() throws InterruptedException {
		// stop playing audio if it is playing
		if (_mediaPlayer != null) {
			_mediaPlayer.stop();
		}

		// delete scm file
		BashCommands delete = new BashCommands("rm -f *.scm");
		delete.startBashProcess();
		delete.getProcess().waitFor();
		_window.close();
	}

	/**
	 * Play or stop the selected audio or playing audio
	 * @throws IOException
	 */
	@FXML
	private void onPlayStopButtonPressed() throws IOException {
		if (_playStopButton.getText().equals("Play")) { // set up preview
			try {
				// determine the factor to resize the audio
				double factor = determineFactor();

				// set up preview voices and play
				if (!_choiceOfVoice.getSelectionModel().getSelectedItem().equals(null)) {
					String choice = _choiceOfVoice.getSelectionModel().getSelectedItem();
					setUpPreview(VOICES_RECORDS.get(choice), factor);
				}
			} catch (NullPointerException e) {
				Alert noVoiceSelectedAlert = createAlert(Alert.AlertType.ERROR, "No Voice Selected", null, "Pick a voice before playing");
				noVoiceSelectedAlert.show();
			}
		} else if (_playStopButton.getText().equals("Stop")) { // stop the preview
			_mediaPlayer.stop();
		}
	}

	/**
	 * Save the previewed audio chunk
	 * @throws Exception
	 */
	@FXML
	private void onSaveButtonPressed() throws Exception {
		try {
			// determine the factor to resize the audio
			double factor = determineFactor();
			setSpeed(factor);

			// Save audio based on user's choice of voice
			if (!_choiceOfVoice.getSelectionModel().getSelectedItem().equals(null)) {
				// set up subtitles
				String choice = _choiceOfVoice.getSelectionModel().getSelectedItem();
				FileWriter text = new FileWriter("selected.txt");
				text.write(_audioText);
				text.close();

				saveAudio(VOICES_RECORDS.get(choice) + ".scm"); // call helper method

				// make a text file with contents being the speech words to later form the subtitles
				FileWriter subtitle = new FileWriter("audio" + (Integer.parseInt(_count.get(0).toString()) - 1) + ".txt");
				subtitle.write(_audioText);
				subtitle.close();

				// delete the text file
				File file = new File("selected.txt");
				file.delete();

				// update audio list
				_controller.updateAudio(_selectedText);

				_combineButton.setDisable(false);
			}
		} catch (NullPointerException e) {
			Alert noVoiceSelectedAlert = createAlert(Alert.AlertType.ERROR, "No Voice Selected", null, "Pick a voice before saving");
			noVoiceSelectedAlert.show();
		}
	}

	/**
	 * Set up the controller
	 * @param selectedtext the selected text
	 * @param scene the scene to manage
	 * @param count the count of audio file
	 * @param controller the controller from previous creation scene
	 * @param combineButton the combine button from creation scene
	 * @throws IOException
	 */
	public void setup(String selectedtext, Scene scene, List<Integer> count, CreationSceneController controller, Button combineButton) throws IOException {
		_controller = controller;
		_count = count;
		_selectedText = selectedtext;
		_previewTextArea.setText(selectedtext);
		_previewTextArea.setEditable(false);
		_previewTextArea.setWrapText(true);
		_audioText = _selectedText.replaceAll("[^a-zA-Z0-9' ]", "");
		_choiceOfVoice.setStyle("-fx-font-size: 1.1em ;");
		_combineButton = combineButton;

		_audioText = _audioText.replaceAll("\"", ""); //remove all instances of " to prevent code breaking

		// Add all voices
		setUpVoices();

		// show window
		_window = new Stage();
		_window.initModality(Modality.APPLICATION_MODAL);
		_window.setScene(scene);
		_window.setResizable(false);

		_window.setOnCloseRequest(windowEvent -> { // set closing the window like cancelling the audio
			try {
				onCancelButtonPressed(); // delete created files
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		_window.show();
	}

	/**
	 * Set up the scm files for all voices
	 * @throws IOException
	 */
	private void setUpVoices() throws IOException {
		// create scm files for all voices setup
		for (String synthesizer : VOICES_RECORDS.values()) {
			FileWriter fileWriter = new FileWriter(synthesizer + ".scm");
			fileWriter.write("(voice_" + synthesizer + ")");
			fileWriter.close();
		}

		// Add all voices
		ObservableList<String> choices = FXCollections.observableArrayList();
		choices.addAll("Default Machine Voice", "Male Voice", "Female Voice");
		_choiceOfVoice.setItems(choices);
		_choiceOfVoice.setValue("Default Machine Voice"); // preset the common case

		// Add all common speeds
		ObservableList<String> speedChoice = FXCollections.observableArrayList();
		speedChoice.addAll("0.25x", "0.5x", "Normal", "1.25x", "1.5x", "2x");
		_choiceOfSpeed.setItems(speedChoice);
		_choiceOfSpeed.setValue("Normal"); // preset the common case
	}

	/**
	 * Set up and start the preview audio
	 * @param choice the choice of voice
	 * @param speed the speed of the audio
	 * @throws IOException
	 */
	private void setUpPreview(String choice, double speed) throws IOException {
		// Write preview scm file
		FileWriter writer = new FileWriter(choice + "_preview.scm");
		writer.write("(voice_" + choice + ")\n(Parameter.set 'Duration_Stretch " + speed + ")");
		writer.close();

		// Write selected text file
		FileWriter text = new FileWriter("selected.txt");
		text.write(_audioText);
		text.close();

		// Make temp audio to play
		BashCommands makeAudio = new BashCommands("text2wave -o temp.wav selected.txt -eval " + choice + "_preview.scm");
		makeAudio.startBashProcess();
		try {
			makeAudio.getProcess().waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Check if the audio is playable
		BufferedReader br = new BufferedReader(new FileReader("temp.wav"));
		if (br.readLine() == null) { // if the audio is unplayable the contents will be null since it didn't create successfully
			tidyUpPreview();
			_saveButton.setDisable(true); // throw an alert if this is the case and give a reason why
			Alert failedVoice = createAlert(Alert.AlertType.ERROR, "Audio Creation Failed", "Failed to make audio clip!", 
					"The selected text contains unpronounceable words for the current selected voice.\n" +
					"Please select a different voice or preview with whole English words in the text only.");
			failedVoice.show();
			br.close();
			return;
		}
		br.close();

		// set up media player
		Media sound = new Media(new File("temp.wav").toURI().toString());
		_mediaPlayer = new MediaPlayer(sound);
		_mediaPlayer.setOnEndOfMedia(() -> tidyUpPreview());
		_mediaPlayer.setOnStopped(() -> tidyUpPreview());
		_mediaPlayer.setOnPlaying(() -> {
			_playStopButton.setText("Stop");
			_saveButton.setDisable(true);
		});
		_mediaPlayer.play();
	}

	/**
	 * Save the audio based on the selected synthesizer
	 * @param synthesizer the synthesizer for the audio
	 * @throws InterruptedException
	 */
	private void saveAudio(String synthesizer) throws InterruptedException {
		// convert text to wav file
		BashCommands tts = new BashCommands("text2wave -o audio" + _count.get(0).toString() + ".wav selected.txt -eval " + synthesizer);
		tts.startBashProcess();
		tts.getProcess().waitFor();            		        

		// update audio count
		_count.set(0, _count.get(0) + 1);

		// show confirmation box
		Alert confirm = createAlert(AlertType.INFORMATION, "Audio saved", "Audio saved successfully, returning to main creation menu", null);
		confirm.showAndWait();
		if (!confirm.isShowing()) {
			_window.close();
		}
	}

	/**
	 * Set the speed of voice in the scm files
	 * @param speed speed of the audio
	 * @throws IOException
	 */
	private void setSpeed(double speed) throws IOException {
		if (speed != 1.0) {
			writeToAllSCMFiles("(Parameter.set 'Duration_Stretch " + speed + ")", true);
		}
	}

	/**
	 * Determine the ratio to scale the audio from the speed
	 * @return a ratio to scale the audio
	 */
	private double determineFactor() {
		double factor;
		String speed = _choiceOfSpeed.getSelectionModel().getSelectedItem();
		if (speed.equals("Normal")) {
			factor = 1.0;
		} else { // perform calculation to find of the speed factor
			factor = 1.0 / Double.parseDouble(speed.substring(0, speed.length() - 1));
		}
		return factor;
	}

	private void tidyUpPreview() {
		_playStopButton.setText("Play");
		_saveButton.setDisable(false);

		// Delete temp files
		File tempAudio = new File("temp.wav");
		File tempText = new File("selected.txt");
		tempAudio.delete();
		tempText.delete();
	}

	/**
	 * Write the statement to all scm files
	 * @param statement the statement to be written in, holds speed information
	 * @param append true if statement is appended to the file, false otherwise
	 * @throws IOException
	 */
	private void writeToAllSCMFiles(String statement, boolean append) throws IOException {
		for (String synthesizer : VOICES_RECORDS.values()) {
			FileWriter fileWriter = new FileWriter(synthesizer + ".scm", append);
			fileWriter.write(statement);
			fileWriter.close();
		}
	}
}