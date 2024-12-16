package org.qba.academicflow;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.transform.*;
import javafx.stage.Stage;
import org.qba.RSGraph.RelationshipGraph;
import org.qba.RSGraph.Vertex;
import org.qba.backend.api.GoogleAPI;
import org.qba.backend.database.Utils;
import org.qba.backend.paper.Paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static javafx.scene.Cursor.H_RESIZE;
import static org.qba.academicflow.Server.log;

public class GraphController {
    @FXML
    public Pane root;
    public HBox infoBar;
    public VBox resultBar;
    public Button backbt;
    public Button savebt;
    private Pane graphPane;
    public VBox setting;
    public VBox dragArea;
    public ImageView closebt;
    public ImageView optbt;
    public VBox menubar;

    private Server.NodeEventBus eventBus;
    private double dragStartX;
    private double initialWidth;
    boolean isDragging = false;
    private RelationshipGraph graph;
    private GoogleAPI api;
    private ExecutorService executor;
    private Paper search_paper;
    private Map<Integer, Paper> paperMap = new HashMap<>();
    private int depth = 2;
    private Future<?> now_build;




    @FXML
    private void initialize(){
        eventBus = Server.NodeEventBus.getInstance();
        menubar.setManaged(false);
        menubar.setVisible(false);
        infoBar.setManaged(false);
        infoBar.setVisible(false);
        resultBar.setFillWidth(true);
        // 获取InfoBar主体VBox的引用
        dragArea.setCursor(H_RESIZE);
        dragArea.setOnMousePressed(e -> {
            dragStartX = e.getX();
            isDragging = true;
        });
        dragArea.setOnMouseDragged(e -> {
            if (isDragging) {
                double deltaX = e.getX() - dragStartX;
                double newWidth = infoBar.getWidth() - deltaX;
                if (newWidth >= 200 && newWidth <= 800) {
                    infoBar.setPrefWidth(newWidth);
                }
            }
        });

        dragArea.setOnMouseReleased(e -> isDragging = false);
        try{
            api = Server.getInstance().getApi();
            executor = Server.getInstance().getExecutor();
            this.graph = new RelationshipGraph();
            graphPane = graph.getRoot();
            graphPane.setManaged(true);
            root.getChildren().add(graphPane);
            graph.startAnimation();
            AnchorPane.setTopAnchor(graphPane, 0.0);
            AnchorPane.setBottomAnchor(graphPane, 0.0);
            AnchorPane.setLeftAnchor(graphPane, 0.0);
            AnchorPane.setRightAnchor(graphPane, 0.0);
        }catch (Exception e){
            e.printStackTrace();
        }
        // 添加鼠标滚轮缩放事件
        graphPane.setOnScroll(event -> {
            // 获取滚轮滚动的增量
            double delta = event.getDeltaY();
            // 设置缩放系数
            double scaleFactor = 1;
            if (delta > 0){
                scaleFactor = 1.02;
            } else if (delta < 0) {
                scaleFactor = 0.98;
            }

            // 获取当前的缩放变换
            Scale scale = getScaleTransform(graphPane);

            // 计算新的缩放比例
            double newScaleX = scale.getX() * scaleFactor;
            double newScaleY = scale.getY() * scaleFactor;

//            // 限制缩放范围（可选）
            newScaleX = Math.max(1, Math.min(newScaleX, 3.0));
            newScaleY = Math.max(1, Math.min(newScaleY, 3.0));

            // 应用新的缩放
            scale.setX(newScaleX);
            scale.setY(newScaleY);

            // 可选：根据鼠标位置调整缩放中心
            // 这有助于实现以鼠标为中心的缩放
            graphPane.setTranslateX(event.getX() * (1 - newScaleX));
            graphPane.setTranslateY(event.getY() * (1 - newScaleY));
        });
        eventBus.subscribe(event->{
            if(event.getEventType()==Server.NodeEvent.PRESSED){
                Platform.runLater(() -> {
                    try {
                        int paperId = event.getNodeid();
                        Paper paper = paperMap.get(paperId);
                        if (paper != null) {
                            VBox box = makeView.createPaperInfoElementSimple(paper);
                            resultBar.getChildren().clear();
                            resultBar.getChildren().add(box);
                            infoBar.setVisible(true);
                            infoBar.setManaged(true);
                            infoBar.toFront();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
        graph.startAnimation();
    }

    public void buildGraph(){
        now_build = executor.submit(new BuildGraph(graph, search_paper));
    }

    public void setSearch_paper(Paper search_paper){
        this.search_paper = search_paper;
    }

    // 辅助方法：获取或创建缩放变换
    private Scale getScaleTransform(Pane node) {
        // 查找是否已存在缩放变换
        for (Transform transform : node.getTransforms()) {
            if (transform instanceof Scale) {
                return (Scale) transform;
            }
        }

        // 如果不存在，创建新的缩放变换
        Scale scale = new Scale(1.0, 1.0);
        node.getTransforms().add(scale);
        return scale;
    }

    public void closeTab(MouseEvent mouseEvent) {
        infoBar.setVisible(false);
    }

    public void optMenuOpen(MouseEvent mouseEvent) {
        if (menubar.isVisible()) {
            menubar.setVisible(false);
            menubar.setManaged(false);
        }
        else {
            menubar.setVisible(true);
            menubar.setManaged(true);
        }
    }

    public void backtomain(MouseEvent mouseEvent) {
        try{
            now_build.cancel(true);
            Server.getInstance().restartApi();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Main.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage)((Node)mouseEvent.getSource()).getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("Main.css").toExternalForm());
            stage.setScene(scene);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void savegraph(MouseEvent mouseEvent) {
        System.out.println("save graph");
        if(!Files.exists(Path.of("./PaperData/paperinfo.sqlite"))){
            try {
                Files.createDirectories(Path.of("./PaperData/"));
                Utils.CreateTable();
            }catch (Exception e){
                log("createDirectories failed");
                e.printStackTrace();
            }
        }
        try{
            Utils.batchInsert(paperMap.values().stream().toList());
        }catch (Exception e){
            log("paper batchInsert failed");
        }

    }

    private class  BuildGraph implements Runnable {
        RelationshipGraph graph;
        Paper paper;

        BuildGraph(RelationshipGraph graph, Paper paper) {
            this.graph = graph;
            this.paper = paper;
        }

        @Override
        public void run() {
            log("set root node:"+paper.toString());
            paperMap.clear();
            Platform.runLater(() -> graph.addRoot(new PaperVertex(paper)));
            Queue<Paper> search_paper_queue = new LinkedList<>();
            Queue<Paper> wait_paper_queue = new LinkedList<>();
            Queue<Integer> search_depth = new LinkedList<>();
            ArrayList<ArrayList<Future<ArrayList<Paper>>>> results = new ArrayList<>();
            Set<Integer> visited = new HashSet<>();
            paperMap.put(paper.get_uid(), paper);
            search_paper_queue.add(paper);
            search_depth.add(0);
            while (true) {
                if (search_paper_queue.isEmpty()) {
                    if (!search_depth.isEmpty()) {
                        for (var futures : results) {
                            int d = search_depth.poll();
                            Paper p = wait_paper_queue.poll();
                            List<Paper> temp = new ArrayList<>();
                            for (var future : futures) {
                                try {
                                    temp.addAll(future.get());
                                } catch (Exception e) {
                                    log(e.getMessage());
                                }
                            }
                            p.setCited_uid(temp.stream().map(Paper::get_uid).map(String::valueOf).collect(Collectors.joining(",")));
                            paperMap.put(p.get_uid(), p);
                            temp.stream().filter(pp -> !visited.contains(pp.get_uid())).forEach(pp -> paperMap.put(pp.get_uid(), pp));
                            temp = Paper.filter(temp, new Paper.CitedFilter()).toList();
                            log("add nodes for "+paper.get_uid());
                            if(d==1){
                                if (temp.size() > 10) {
                                    temp = Paper.filter(temp, new Paper.CitedCountFilter(10)).toList();
                                }
                            }
                            else if(d==2){
                                if (temp.size() > 3) {
                                    temp = Paper.filter(temp, new Paper.CitedCountFilter(3)).toList();
                                }
                            }
                            else if(d>=3){
                                if (temp.size() > 1) {
                                    temp = Paper.filter(temp, new Paper.CitedCountFilter(1)).toList();
                                }
                            }
                            List<Paper> finalTemp = temp;
                            Platform.runLater(() -> {
                                graph.addNodes(finalTemp.stream().map(PaperVertex::new).toList());
                                graph.addEdges(p.get_uid(), finalTemp.stream().map(Paper::get_uid).toList());
                            });
                            temp.forEach(pp -> {
                                search_depth.offer(d);
                                search_paper_queue.offer(pp);
                            });
                        }
                        results.clear();
                    } else {
                        break;
                    }
                }
                Paper p = search_paper_queue.poll();
                wait_paper_queue.add(p);
                int d = search_depth.poll();
                log("Search:" + p.getTitle());
                if (d > depth) {
                    break;
                }
                if (visited.contains(p.get_uid())) {
                    continue;
                }
                visited.add(p.get_uid());
                if(d<=2){
                    results.add(api.GetFutureFromCited(p.getCited_url(), 20));
                }else {
                    results.add(api.GetFutureFromCited(p.getCited_url(), 10));
                }
                search_depth.add(d + 1);
            }
        }
    }
    private static class PaperVertex extends Vertex {
        Paper paper;
        PaperVertex(Paper paper) {
            this.paper = paper;
        }
        @Override
        public int getId() {
            return paper.get_uid();
        }

        @Override
        public String getLabel() {
            if(paper.getAuthor().length()>10){
                return paper.getAuthor().substring(0, 10)+"\n"+paper.getYear();
            }
            return paper.getAuthor()+"\n"+paper.getYear();
        }

        @Override
        public Double getsize() {
            int c = paper.getCited_count();
            if (c <= 10) {
                return 5.0;
            }
            else if (c <= 100) {
                return 10.0;
            }
            else{
                return 15.0;
            }
        }
    }

}