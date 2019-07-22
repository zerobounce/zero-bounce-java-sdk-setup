package sample;

import com.zerobounce.ZeroBounceSDK;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

import java.io.File;
import java.util.Date;

public class Controller {

    public Button creditsButton;
    public Button fileStatusButton;
    public Button getFileButton;
    public Button apiUsageButton;
    public Button deleteFileButton;
    @FXML
    Button validateButton;
    @FXML
    Button pickFileButton;


    public void initialize() {

        System.out.println("Controller::initialize");
        ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>");


        validateButton.setOnAction(event -> validate("<EMAIL_TO_TEST>"));

        creditsButton.setOnAction(event -> getCredits());

        apiUsageButton.setOnAction(event -> getApiUsage());

        pickFileButton.setOnAction(event -> sendFile());

        fileStatusButton.setOnAction(event -> fileStatus("<YOUR_FILE_ID>"));

        getFileButton.setOnAction(event -> getFile("<YOUR_FILE_ID>"));

        deleteFileButton.setOnAction(event -> deleteFile("<YOUR_FILE_ID>"));
    }

    private void validate(String email) {
        ZeroBounceSDK.getInstance().validate(
                email,
                null,
                response -> System.out.println("Controller::validate response=" + response.toString()),
                errorMessage -> System.out.println("Controller::validate error=" + errorMessage));
    }

    private void getCredits() {
        ZeroBounceSDK.getInstance().getCredits(
                response -> System.out.println("Controller::getCredits response=" + response.toString()),
                errorMessage -> System.out.println("Controller::getCredits error=" + errorMessage));
    }

    private void getApiUsage() {
        Date startDate = new Date();
        startDate.setTime(startDate.getTime() - 5 * 24 * 60 * 60 * 1000); // previous 5 days
        Date endDate = new Date();
        ZeroBounceSDK.getInstance().getApiUsage(startDate, endDate,
                response -> System.out.println("Controller::getApiUsage response=" + response.toString()),
                errorMessage -> System.out.println("Controller::getApiUsage error=" + errorMessage));
    }

    private void sendFile() {
        File file = new File("./email_file.csv");
        ZeroBounceSDK.getInstance().sendFile(
                file, 1,
                response -> System.out.println("Controller::getCredits response=" + response.toString()),
                errorMessage -> System.out.println("Controller::getCredits error=" + errorMessage),
                new ZeroBounceSDK.SendFileOptions().setHasHeaderRow(true).setFirstNameColumn(2).setLastNameColumn(3));
    }

    private void fileStatus(String fileId) {
        ZeroBounceSDK.getInstance().fileStatus(fileId,
                response -> System.out.println("Controller::getCredits response=" + response.toString()),
                errorMessage -> System.out.println("Controller::getCredits error=" + errorMessage));
    }

    private void getFile(String fileId) {
        ZeroBounceSDK.getInstance().getFile(fileId, "./downloads/file.csv",
                response -> System.out.println("Controller::getCredits response=" + response.toString()),
                errorMessage -> System.out.println("Controller::getCredits error=" + errorMessage));
    }

    private void deleteFile(String fileId) {
        ZeroBounceSDK.getInstance().deleteFile(fileId,
                response -> System.out.println("Controller::getCredits response=" + response.toString()),
                errorMessage -> System.out.println("Controller::getCredits error=" + errorMessage));
    }
}
