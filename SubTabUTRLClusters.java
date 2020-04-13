/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tappas;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 *
 * @author Hector del Risco - hdelrisco@ufl.edu & Pedro Salguero - psalguero@cipf.es
 */
public class SubTabUTRLClusters extends SubTabBase {
    AnchorPane paneContents;
    TableView tblSummary;
    Label lblSummary;
    Pane paneCluster;
    ChoiceBox cbClusters;
    ContextMenu cmSummary;
    ImageView imgChart;
    Button btnAllClusters;

    String panel;
    boolean hasData = false;
    DataUTRL utrlData;
    String utrlColTitle;
    DlgUTRLAnalysis.Params utrlParams;
    ObservableList<DataUTRL.UTRLSelectionResults> data = null;
    String analysisId, utrlId;

    public SubTabUTRLClusters(Project project) {
        super(project);
    }
    @Override
    public boolean initialize(TabBase tabBase, SubTabInfo subTabInfo, HashMap<String, Object> args) {
        subTabInfo.btnExport = true;
        boolean result = super.initialize(tabBase, subTabInfo, args);
        if(result) {
            utrlData = new DataUTRL(project);
            panel = TabGeneDataViz.Panels.TRANS.name();
            hasData = utrlData.hasUTRLData();
            utrlColTitle = "Gene";
            utrlId = "Genes";

            if(args.containsKey("id")) {
                analysisId = (String) args.get("id");
                subTabInfo.title = "UTRL Clusters: " + analysisId;
                subTabInfo.tooltip = "UTRL Cluster Profiles for '" + analysisId + "'";
                setTabLabel(subTabInfo.title, subTabInfo.tooltip);

                // we must have UTRL results or we have no business being here
                if(!hasData)
                    result = false;
            }
            else
                result = false;
            
            if(result) {
                // parameter data file is required
                result = Files.exists(Paths.get(utrlData.getUTRLParamsFilepath()));
                if(result) {
                    utrlParams = DlgUTRLAnalysis.Params.load(utrlData.getUTRLParamsFilepath(), project);

                    // cluster file is required
                    result = Files.exists(Paths.get(utrlData.getUTRLClusterMembersFilepath(analysisId)));
                    if(!result)
                        app.ctls.alertInformation("UTRL Clusters", "Missing cluster data file");
                }
                else
                    app.ctls.alertInformation("UTRL Clusters", "Missing UTRL parameter file");
            }
            
            if(!result)
                subTabInfo.subTabBase = null;
        }
        return result;
    }

    //
    // Internal Functions
    //
    private void showSubTab() {
        paneContents = (AnchorPane) tabNode.lookup("#paneContents");
        paneCluster = (Pane) tabNode.lookup("#paneCluster");
        cbClusters = (ChoiceBox) tabNode.lookup("#cbClusters");
        imgChart = (ImageView) tabNode.lookup("#imgChart");
        lblSummary = (Label) tabNode.lookup("#lblSummary");
        tblSummary = (TableView) tabNode.lookup("#tblSummary");
        btnAllClusters = (Button) tabNode.lookup("#btnAllClusters");
        btnAllClusters.layoutXProperty().bind(Bindings.subtract(paneContents.widthProperty(), Bindings.add(btnAllClusters.widthProperty(), 10.0)));
        
        // setup cluster display
        imgChart.fitHeightProperty().bind(paneCluster.heightProperty());
        imgChart.fitWidthProperty().bind(paneCluster.widthProperty());
        imgChart.setPreserveRatio(true);
        app.ctls.setupImageExport(imgChart, "UTRL Cluster Expression Profile Plot", "tappAS_UTRL" + Utils.properCase(utrlId) + "_ExpProfileCluster_Plot.png", null);
        
        // populate data summary table
        ObservableList<TableColumn> cols = tblSummary.getColumns();
        cols.get(0).setText(utrlColTitle);
        cols.get(0).setCellValueFactory(new PropertyValueFactory<>("Id"));
        cols.get(1).setCellValueFactory(new PropertyValueFactory<>("GeneDescription"));
        addRowNumbersCol(tblSummary);
        if(tblSummary.getItems().size() > 0)
            tblSummary.getSelectionModel().select(0);
        app.export.addCopyToClipboardHandler(tblSummary);
        ContextMenu cm = new ContextMenu();
        addGeneDVItem(cm, tblSummary, panel);
        app.export.setupSimpleTableExport(tblSummary, cm, true, "tappAS_UTRL_" + Utils.properCase(utrlId) + "_ClusterExpProfile.tsv");
        tblSummary.setContextMenu(cm);
        app.export.addCopyToClipboardHandler(tblSummary);

        // setup list of clusters
        HashMap<String, Integer> hmClusters = new HashMap<>();
        ArrayList<Integer> lstClusterNums = new ArrayList<>();
        String id = analysisId.substring(0,analysisId.length()-"_UTRX".length());
        // we supose a max of 4 different groups !!!
        for(DataUTRL.UTRLSelectionResults dsr : data) {
            int clnum = -1;
            String[] names = project.data.getGroupNames();
            //max 4 groups
            if(id.equals(names[0])){
                clnum = dsr.getCluster().equals("")? 0 : Integer.parseInt(dsr.getCluster());
            }else if(id.equals(names[1])){
                clnum = dsr.getClusterCmp1().equals("")? 0 : Integer.parseInt(dsr.getClusterCmp1());
            }else if(id.equals(names[2])){
                clnum = dsr.getClusterCmp2().equals("")? 0 : Integer.parseInt(dsr.getClusterCmp2());
            }else if(id.equals(names[3])){
                clnum = dsr.getClusterCmp3().equals("")? 0 : Integer.parseInt(dsr.getClusterCmp3());
            }
            if(clnum != 0) {
                if(!hmClusters.containsKey("Cluster " + clnum)) {
                    lstClusterNums.add(clnum);
                    hmClusters.put("Cluster " + clnum, clnum);
                }
            }
        }
        ArrayList<String> lstClusters = new ArrayList<>();
        lstClusters.addAll(hmClusters.keySet());
        Collections.sort(lstClusters, String.CASE_INSENSITIVE_ORDER);
        btnAllClusters.setOnAction((event) -> { app.ctlr.drillDownUTRLClusters(utrlColTitle, analysisId, lstClusterNums); });

        // populate clusters selection
        for(String cluster : lstClusters)
            cbClusters.getItems().add(cluster);
        cbClusters.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> ov, Number oldValue, Number newValue) {
                if(newValue != null && (int) newValue != -1) {
                    int cluster = hmClusters.get((String)cbClusters.getItems().get((int) newValue));
                    lblSummary.setText("Cluster " + cluster);
                    ObservableList<DataUTRL.UTRLSelectionResults> matchItems = FXCollections.observableArrayList();
                    for(DataUTRL.UTRLSelectionResults dsr : data) {
                        int clnum = -1;
                        String[] names = project.data.getGroupNames();
                        //max 4 groups
                        if(id.equals(names[0])){
                            clnum = dsr.getCluster().equals("")? 0 : Integer.parseInt(dsr.getCluster());
                        }else if(id.equals(names[1])){
                            clnum = dsr.getClusterCmp1().equals("")? 0 : Integer.parseInt(dsr.getClusterCmp1());
                        }else if(id.equals(names[2])){
                            clnum = dsr.getClusterCmp2().equals("")? 0 : Integer.parseInt(dsr.getClusterCmp2());
                        }else if(id.equals(names[3])){
                            clnum = dsr.getClusterCmp3().equals("")? 0 : Integer.parseInt(dsr.getClusterCmp3());
                        }
                        
                        if(clnum == cluster)
                            matchItems.add(dsr);
                    }
                    tblSummary.setItems(matchItems);
                    String filepath = utrlData.getUTRLClusterImageFilepath(analysisId, cluster);
                    if(Files.exists(Paths.get(filepath))) {
                        showProfileImage(filepath);
                    }
                }
            }
        });
        if(cbClusters.getItems().size() > 0)
            cbClusters.getSelectionModel().select(0);
        
        setupExportMenu();
        setFocusNode(tblSummary, true);
    }
    private void showProfileImage(String filepath) {
        Image img = new Image("file:" + filepath);
        imgChart.setImage(img);
        imgChart.setVisible(true);
    }
    private void setupExportMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem item = new MenuItem("Export UTRL cluster expression profile summary table data...");
        item.setOnAction((event) -> { app.export.exportTableData(tblSummary, true, "tappAS_UTRL_" + Utils.properCase(utrlId) + "_ClusterExpProfile.tsv"); });
        cm.getItems().add(item);
        cm.getItems().add(new SeparatorMenuItem());
        item = new MenuItem("Export UTRL cluster expression profile plot image...");
        item.setOnAction((event) -> { 
            app.export.exportImage("UTRL Cluster Expression Profile Plot", "tappAS_UTRL_" + Utils.properCase(utrlId) + "_ClusterExpProfile_Plot.png", imgChart.getImage());
        });
        cm.getItems().add(item);
        cm.getItems().add(new SeparatorMenuItem());
        item = new MenuItem("Export subtab contents image...");
        item.setOnAction((event) -> { 
            SnapshotParameters sP = new SnapshotParameters();
            double x = paneContents.getWidth();
            double ratio = 3840/x;
            paneContents.setScaleX(ratio);
            paneContents.setScaleY(ratio);
            WritableImage img = paneContents.snapshot(sP, null);
            double newX = paneContents.getWidth();
            ratio = x/newX;
            paneContents.setScaleX(ratio);
            paneContents.setScaleY(ratio);
            
            //WritableImage img = paneContents.snapshot(new SnapshotParameters(), null);
            app.export.exportImage("UTRL Cluster Expression Profile Panel", "tappAS_UTRL_" + Utils.properCase(utrlId) + "_ClusterExpProfile_Panel.png", (Image)img);
        });
        cm.getItems().add(item);
        subTabInfo.cmExport = cm;
    } 
    
    //
    // Data Load
    //
    
    // NOTE: this is only loading the PCA plot's data so the rest of the form is displayed
    //       immediately and only the plot is displayed after the data is obtained (if not already done)
    @Override
    protected void runDataLoadThread() {
        data = utrlData.getUTRLSelectionResults(true);
        FXCollections.sort(data);
        showSubTab();
    }
}
