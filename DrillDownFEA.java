/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tappas;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.stage.Window;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 *
 * @author Hector del Risco - hdelrisco@ufl.edu & Pedro Salguero - psalguero@cipf.es
 */
public class DrillDownFEA extends DlgBase {
    static int id = 0;
    static String exportFilename = "";

    Label lblHeader, lblDescription;
    Button btnHelpTbl, btnExportTbl;
    TableView tblMembers;
    Pane paneMenu;
    ProgressIndicator pi;

    int toolId;
    DataApp.DataType dataType;
    String idColName;
    String featureId;
    String fileFeatures;
    String panel;
    ArrayList<String> lstTestItems;
    TaskHandler.ServiceExt service = null;
    ObservableList<FEADrillDownData> data = null;
    
    public DrillDownFEA(Project project, Window window) {
        super(project, window);
        toolId = ++id;
    }
    public HashMap<String, String> showAndWait(DataApp.DataType dataType, String featureId, String description, String fileFeatures, ArrayList<String> lstTestItems) {
        if(createToolDialog("DrillDownFEA.fxml", "FEA Features Drill Down Data",  null)) {
            this.dataType = dataType;
            this.featureId = featureId;
            this.fileFeatures = fileFeatures;
            this.lstTestItems = lstTestItems;
            panel = TabGeneDataViz.Panels.TRANS.name();
            switch(dataType) {
                case PROTEIN:
                    idColName = "Protein";
                    panel = TabGeneDataViz.Panels.PROTEIN.name();
                    break;
                case GENE:
                    idColName = "Gene";
                    break;
                case TRANS:
                default:
                    idColName = "Transcript";
                    break;
            }
            
            // get control objects
            lblHeader = (Label) scene.lookup("#lblHeader");
            lblDescription = (Label) scene.lookup("#lblDescription");
            pi = (ProgressIndicator) scene.lookup("#piImage");
            tblMembers = (TableView) scene.lookup("#tblMembers");
            paneMenu = (Pane) scene.lookup("#paneMenu");

            // setup dialog
            exportFilename = "tappAS_FEA_"+featureId+".tsv";
            dialog.setTitle("FEA Drill Down Data for " + featureId);
            lblHeader.setText(idColName + "s containing " + featureId);
            lblDescription.setText(description);
            tblMembers.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            ObservableList<TableColumn> cols = tblMembers.getColumns();
            cols.get(0).setText(idColName);
            cols.get(0).setCellValueFactory(new PropertyValueFactory<>("Member"));
            cols.get(1).setCellValueFactory(new PropertyValueFactory<>("Contained"));
            cols.get(2).setCellValueFactory(new PropertyValueFactory<>("Gene"));
            cols.get(3).setCellValueFactory(new PropertyValueFactory<>("GeneDescription"));
            if(dataType.equals(DataApp.DataType.GENE))
                cols.remove(2);
            SubTabBase.addRowNumbersCol(tblMembers);
            ContextMenu cm = new ContextMenu();
            addGeneDVItem(cm, tblMembers, panel);
            app.export.setupTableExport(this, cm, true);
            tblMembers.setContextMenu(cm);
            app.export.addCopyToClipboardHandler(tblMembers);
            
            // setup menu buttons
            double yoffset = 3.0;
            btnExportTbl = app.ctls.addImgButton(paneMenu, "export.png", "Export table data...", yoffset, 32, true);
            btnExportTbl.setOnAction((event) -> { onButtonExport(); });
            yoffset += 36;
            btnHelpTbl = app.ctls.addImgButton(paneMenu, "help.png", "Help", yoffset, 32, true);
            btnHelpTbl.setOnAction((event) -> { DlgHelp dlg = new DlgHelp(); dlg.show(title, "Help_DrillDown_FEA.html", Tappas.getWindow()); });
            
            // process dialog - modeless
            dialog.setOnShown(e -> { runThread();});
            dialog.showAndWait();
        }
        return null;
    }
    @Override
    protected void onButtonExport() {
        DlgExportData.Config cfg = new DlgExportData.Config(true, idColName + "s list (IDs only)", false, "");
        DlgExportData.Params results = app.ctlr.getExportDataParams(cfg, null, null);
        if(results != null) {
            HashMap<String, String> hmColNames = new HashMap<>();
            Export.ExportSelection selection = Export.ExportSelection.All;
            if(results.dataSelection.equals(DlgExportData.Params.DataSelection.SELECTEDROWS))
                selection = Export.ExportSelection.Highlighted;
            if(results.dataType.equals(DlgExportData.Params.DataType.TABLEROWS.name()))
                app.export.exportTableDataToFile(tblMembers, selection, exportFilename);
            else if(results.dataType.equals(DlgExportData.Params.DataType.LIST.name())) {
                hmColNames.put(idColName, "");
                app.export.exportTableDataListToFile(tblMembers, selection, hmColNames, exportFilename);
            }
        }
    }    

    //
    // Internal Functions
    //
    protected void addGeneDVItem(ContextMenu cm, TableView tbl, String panel) {
        MenuItem item = new MenuItem("Show gene data visualization");
        cm.getItems().add(item);
        item.setOnAction((event) -> {
            // get highlighted row's data and show gene data visualization
            ObservableList<Integer> lstIdxs = tbl.getSelectionModel().getSelectedIndices();
            if(lstIdxs.size() != 1) {
                String msg = "You have multiple gene rows highlighted.\nHighlight a single row with the gene you want to visualize.";
                app.ctls.alertInformation("Display Gene Data Visualization", msg);
            }
            else {
                FEADrillDownData dd = (FEADrillDownData) tbl.getItems().get((tbl.getSelectionModel().getSelectedIndex()));
                String gene = dd.getGene();
                String genes[] = gene.split(",");
                if(genes.length > 1) {
                    List<String> lst = Arrays.asList(genes);
                    Collections.sort(lst);
                    ChoiceDialog dlg = new ChoiceDialog(lst.get(0), lst);
                    dlg.setTitle("Gene Data Visualization");
                    dlg.setHeaderText("Select gene to visualize");
                    Optional<String> result = dlg.showAndWait();
                    if(result.isPresent()) {
                        gene = result.get();
                        showGeneDataVisualization(gene, panel);
                    }
                }
                else
                    showGeneDataVisualization(gene, panel);
            }
        });
    }
    private void showGeneDataVisualization(String gene, String panel) {
        HashMap<String, Object> args = new HashMap<>();
        args.put("panels", panel);
        app.tabs.openTabGeneDataViz(project, project.getDef().id, gene, project.data.getResultsGeneTrans().get(gene), "Project '" + project.getDef().name + "'", args);
    }
    
    //
    // Data Load
    //
    private void runThread() {
        // get script path and run service/task
        service = new DataService();
        service.initialize();
        service.start();
    }
    private class DataService extends TaskHandler.ServiceExt {
        @Override
        public void initialize() {
            data = null;
        }
        @Override
        protected void onRunning() {
            pi.setVisible(true);
        }
        @Override
        protected void onStopped() {
            pi.setVisible(false);
        }
        @Override
        protected void onFailed() {
            java.lang.Throwable e = getException();
            if(e != null)
                app.logWarning("DrillDownFEA - task aborted. Exception: " + e.getMessage());
            else
                app.logWarning("DrillDownFEA - task aborted.");
            app.ctls.alertWarning("Drill Down Funtional Enrichment Analysis", "DrillDownFEA - task aborted");
        }
        @Override
        protected void onSucceeded() {
            if(data != null) {
                tblMembers.setItems(data);
                if(!data.isEmpty())
                    tblMembers.getSelectionModel().select(0);
            }
            else
                app.ctls.alertWarning("FEA Drill Down Data", "Unable to load drill down data.");
        }
        @Override
        protected Task<Void> createTask() {
            Task task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    try {
                        if(Files.exists(Paths.get(fileFeatures))) {
                            ObservableList<FEADrillDownData> lst = FXCollections.observableArrayList();
                            List<String> lines = Files.readAllLines(Paths.get(fileFeatures), StandardCharsets.UTF_8);
                            for(String line : lines) {
                                line = line.trim();
                                if(!line.isEmpty() && line.charAt(0) != '#') {
                                    String[] fields = line.split("\t");
                                    if(fields.length > 2) {
                                        String gene = project.data.getGenes(dataType, fields[0]);
                                        if(fields[1].equals(featureId)) {
                                            String geneDescription = project.data.getGeneDescriptions(dataType, fields[0]);
                                            lst.add(new FEADrillDownData(fields[0], gene, geneDescription, lstTestItems.contains(fields[0])? "YES" : "NO"));
                                        }
                                    }
                                    else {
                                        app.logError("Invalid line, " + line + ", in list file '" + fileFeatures + "'.");
                                        lst.clear();
                                        break;
                                    }
                                }
                            }
                            FXCollections.sort(lst);
                            data = lst;
                        }
                    }
                    catch (Exception e) {
                        app.logError("Unable to load list file '" + fileFeatures + "': " + e.getMessage());
                    }
                    
                    return null;
                }
            };
            taskInfo = new TaskHandler.TaskInfo("FEA Drill Down Data " + toolId, task);
            return task;
        }
    }
    
    //
    // Data Classes
    //    
    static public class FEADrillDownData implements Comparable<FEADrillDownData> {
        public SimpleStringProperty member;
        public SimpleStringProperty gene;
        public SimpleStringProperty geneDescription;
        public SimpleStringProperty contained;
        public FEADrillDownData(String member, String gene, String geneDescription, String contained) {
            this.member = new SimpleStringProperty(member);
            this.gene = new SimpleStringProperty(gene);
            this.geneDescription = new SimpleStringProperty(geneDescription);
            this.contained = new SimpleStringProperty(contained);
        }
        public String getMember() { return member.get(); }
        public String getGene() { return gene.get(); }
        public String getGeneDescription() { return geneDescription.get(); }
        public String getContained() { return contained.get(); }
        
        @Override
        public int compareTo(FEADrillDownData td) {
            return (member.get().compareTo(td.member.get()));
        }
    }
}
