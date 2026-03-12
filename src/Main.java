/*
 * Decompiled with CFR 0.152.
 */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public class Main
extends JFrame {
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^.!?\u3002\uff01\uff1f\\n]+[.!?\u3002\uff01\uff1f]?|\\n+");
    private static final String DEFAULT_PLAYER_NAME = "";
    private static final int MAX_PAGE_CHARS = 72;
    private static final int STORY_PANEL_HEIGHT = 196;
    private static final int HUD_SIDE_MARGIN = 18;
    private static final int HUD_BOTTOM_MARGIN = 14;
    private static final int SPEAKER_AREA_HEIGHT = 42;
    private static final int CHARACTER_TOP_MARGIN = 20;
    private static final int STORY_TEXT_TOP_PADDING = 18;
    private static final int STORY_TEXT_SIDE_PADDING = 18;
    private static final int STORY_TEXT_BOTTOM_PADDING = 24;
    private static final Path SAVE_PATH = Path.of("out", "save.properties");
    private static final Path SCENES_PATH = Path.of("assets", "scenes.json");
    private static final double MOBILE_ASPECT_RATIO = 0.5625;
    private static final Color NIGHT = new Color(10, 12, 18);
    private static final Color NIGHT_DEEP = new Color(17, 21, 31);
    private static final Color PANEL = new Color(56, 62, 74);
    private static final Color PANEL_SOFT = new Color(66, 73, 86);
    private static final Color PANEL_EDGE = new Color(101, 110, 128);
    private static final Color ACCENT = new Color(146, 158, 182);
    private static final Color ACCENT_SOFT = new Color(122, 132, 150);
    private static final Color ACCENT_GLOW = new Color(118, 128, 154, 42);
    private static final Color BUTTON = new Color(72, 79, 92);
    private static final Color BUTTON_HOVER = new Color(86, 95, 110);
    // 상단 UI: 장면 제목과 챕터명을 보여준다.
    private final JLabel titleLabel = new JLabel("", 2);
    private final JLabel chapterLabel = new JLabel("", 4);
    // 장면 레이어: 배경, 캐릭터, 각종 오버레이를 순서대로 쌓는다.
    private final JLabel backgroundLabel = new JLabel("", 0);
    private final JLabel characterLabel = new JLabel("", 0);
    private final JPanel scenePanel = new JPanel(null);
    private final JPanel overlayPanel = new JPanel(new BorderLayout());
    // 하단 대화창 구성 요소: 카드, 본문 텍스트, 화자 이름표
    private final JPanel hudPanel = new JPanel(null);
    private final StoryTextPanel storyArea = new StoryTextPanel();
    private final JPanel storyPanel = this.createStoryPanel();
    private final JPanel speakerPanel = new JPanel(new FlowLayout(0, 0, 0));
    private final JLabel speakerBadge = new JLabel(" ", 2);
    // 오버레이 UI: 선택지, 시작화면, 엔딩 모음을 필요할 때만 덮어 띄운다.
    private final JPanel choiceOverlay = new JPanel(null);
    private final JPanel choicesPanel = new JPanel();
    private final JPanel startOverlay = new JPanel(new GridBagLayout());
    private final JPanel galleryOverlay = new JPanel(new GridBagLayout());
    // 시작 화면 입력과 이미지 캐시
    private final JTextField nameField = new JTextField("");
    private final JPanel galleryListPanel = new JPanel();
    private final Map<String, BufferedImage> imageCache = new LinkedHashMap<String, BufferedImage>();
    private JButton continueGameButton;
    // 런타임 데이터: 장면 맵, 현재 게임 상태, 엔딩 설명
    private final Map<String, Scene> scenes = new LinkedHashMap<String, Scene>();
    private final GameState state = new GameState();
    private final Map<String, String> endingTitles = new LinkedHashMap<String, String>();
    private final Map<String, String> endingDescriptions = new LinkedHashMap<String, String>();
    private Scene currentScene;
    private Timer dialogueTimer;
    private boolean adjustingFrame;
    private List<PageEntry> scenePages = List.of();
    private List<Choice> visibleChoices = List.of();
    private int currentPageIndex;
    private PageEntry currentPage = PageEntry.empty();
    private boolean pageFullyVisible;
    private String currentSceneId = "";
    private String activeBackgroundImage = "";
    private String activeCharacterImage = "";
    private int textSpeedMs = 18;
    private Dimension lastFrameSize = new Dimension(450, 800);

    public Main() {
        this.setTitle("\uc6d4\uc57c\uace0\ub4f1\ud559\uad50: \uce68\ubb35\uc758 \uae30\ub85d");
        this.setDefaultCloseOperation(3);
        this.setSize(450, 800);
        this.setMinimumSize(new Dimension(360, 640));
        this.setLocationRelativeTo(null);
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setBackground(NIGHT);
        jPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        this.setContentPane(jPanel);
        JPanel jPanel2 = this.createTopBar();
        this.titleLabel.setForeground(new Color(220, 225, 233));
        this.titleLabel.setFont(new Font("Malgun Gothic", 1, 24));
        this.titleLabel.setBorder(new EmptyBorder(6, 8, 10, 8));
        this.chapterLabel.setForeground(new Color(160, 171, 192));
        this.chapterLabel.setFont(new Font("Malgun Gothic", 0, 12));
        this.chapterLabel.setBorder(new EmptyBorder(10, 8, 10, 8));
        jPanel2.add((Component)this.titleLabel, "West");
        jPanel2.add((Component)this.chapterLabel, "East");
        jPanel.add((Component)jPanel2, "North");
        this.scenePanel.setOpaque(false);
        jPanel.add((Component)this.scenePanel, "Center");
        this.backgroundLabel.setOpaque(true);
        this.backgroundLabel.setBackground(new Color(12, 16, 24));
        this.backgroundLabel.setForeground(new Color(170, 178, 194));
        this.backgroundLabel.setFont(new Font("Malgun Gothic", 0, 18));
        this.backgroundLabel.setHorizontalAlignment(0);
        this.backgroundLabel.setVerticalAlignment(0);
        this.scenePanel.add(this.backgroundLabel);
        this.characterLabel.setOpaque(false);
        this.characterLabel.setHorizontalAlignment(0);
        this.characterLabel.setVerticalAlignment(0);
        this.characterLabel.setForeground(new Color(240, 244, 250));
        this.characterLabel.setFont(new Font("Malgun Gothic", 1, 16));
        this.scenePanel.add(this.characterLabel);
        this.overlayPanel.setOpaque(false);
        this.scenePanel.add(this.overlayPanel);
        this.storyArea.setFont(new Font("Malgun Gothic", 0, 18));
        this.storyArea.setForeground(new Color(229, 233, 239));
        this.storyArea.setOpaque(false);
        this.storyArea.setBorder(new EmptyBorder(18, 18, 24, 18));
        this.storyArea.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                Main.this.advanceStory();
            }
        });
        this.speakerBadge.setFont(new Font("Malgun Gothic", 1, 14));
        this.speakerBadge.setForeground(new Color(238, 241, 246));
        this.speakerBadge.setOpaque(true);
        this.speakerBadge.setBackground(new Color(88, 96, 116));
        this.speakerBadge.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT_SOFT, 1), BorderFactory.createLineBorder(new Color(255, 255, 255, 20), 1)), new EmptyBorder(7, 12, 7, 12)));
        this.speakerPanel.setOpaque(false);
        this.speakerPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        this.speakerPanel.add(this.speakerBadge);
        this.scenePanel.add(this.speakerPanel);
        this.storyPanel.setOpaque(false);
        this.storyPanel.setLayout(null);
        this.storyPanel.setPreferredSize(new Dimension(0, 196));
        this.storyPanel.setMinimumSize(new Dimension(0, 196));
        this.storyPanel.add(this.storyArea);
        this.storyPanel.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                Main.this.advanceStory();
            }
        });
        this.hudPanel.setOpaque(false);
        this.hudPanel.add(this.storyPanel);
        this.choiceOverlay.setOpaque(false);
        this.choicesPanel.setOpaque(true);
        this.choicesPanel.setLayout(new GridLayout(0, 2, 12, 12));
        this.choicesPanel.setBackground(PANEL);
        this.choicesPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(PANEL_EDGE, 1), BorderFactory.createLineBorder(new Color(255, 255, 255, 28), 1)), new EmptyBorder(16, 16, 16, 16)));
        this.choiceOverlay.add(this.choicesPanel);
        this.choiceOverlay.setVisible(false);
        this.startOverlay.setOpaque(false);
        this.startOverlay.add(this.createStartPanel());
        this.galleryOverlay.setOpaque(false);
        this.galleryOverlay.add(this.createGalleryPanel());
        this.galleryOverlay.setVisible(false);
        this.overlayPanel.add((Component)this.createBottomShade(), "Center");
        this.overlayPanel.add((Component)this.choiceOverlay, "Center");
        this.scenePanel.add(this.hudPanel);
        this.scenePanel.add(this.startOverlay);
        this.scenePanel.add(this.galleryOverlay);
        this.initEndingMetadata();
        this.initScenes();
        this.resetState();
        this.loadSaveData();
        this.installKeyBindings();
        this.addComponentListener(new ComponentAdapter(){

            @Override
            public void componentResized(ComponentEvent componentEvent) {
                Main.this.enforceMobileAspectRatio();
                Main.this.layoutSceneLayers();
                Main.this.refreshImages();
            }
        });
        this.showStartScreen();
        SwingUtilities.invokeLater(() -> {
            this.layoutSceneLayers();
            this.refreshImages();
            this.scenePanel.repaint();
        });
    }

    private JComponent createBottomShade() {
        return new JPanel(){

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D graphics2D = (Graphics2D)graphics.create();
                GradientPaint gradientPaint = new GradientPaint(0.0f, 0.0f, new Color(5, 7, 10, 0), 0.0f, this.getHeight(), new Color(8, 10, 16, 210));
                graphics2D.setPaint(gradientPaint);
                graphics2D.fillRect(0, 0, this.getWidth(), this.getHeight());
                graphics2D.setPaint(new GradientPaint(0.0f, (float)this.getHeight() / 3.0f, new Color(0, 0, 0, 0), 0.0f, this.getHeight(), ACCENT_GLOW));
                graphics2D.fillRect(0, 0, this.getWidth(), this.getHeight());
                graphics2D.dispose();
            }
        };
    }

    private JPanel createTopBar() {
        JPanel jPanel = new JPanel(new BorderLayout()){

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D graphics2D = (Graphics2D)graphics.create();
                graphics2D.setColor(new Color(52, 58, 69, 236));
                graphics2D.fillRoundRect(0, 0, this.getWidth(), this.getHeight(), 18, 18);
                graphics2D.setColor(new Color(198, 206, 220, 70));
                graphics2D.drawRoundRect(0, 0, this.getWidth() - 1, this.getHeight() - 1, 18, 18);
                graphics2D.dispose();
            }
        };
        jPanel.setOpaque(false);
        return jPanel;
    }

    private JPanel createStoryPanel() {
        return new JPanel(new BorderLayout(0, 8)){

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D graphics2D = (Graphics2D)graphics.create();
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int n = 6;
                int n2 = 6;
                int n3 = 6;
                int n4 = Math.max(1, this.getWidth() - n * 2);
                int n5 = Math.max(1, this.getHeight() - n2 - n3);
                graphics2D.setColor(new Color(0, 0, 0, 46));
                graphics2D.fillRoundRect(n, n2, n4, n5, 24, 24);
                graphics2D.setColor(new Color(52, 58, 69, 236));
                graphics2D.fillRoundRect(0, 0, Math.max(1, this.getWidth() - 1), Math.max(1, this.getHeight() - 1), 24, 24);
                graphics2D.setColor(new Color(198, 206, 220, 70));
                graphics2D.drawRoundRect(1, 1, Math.max(1, this.getWidth() - 3), Math.max(1, this.getHeight() - 3), 22, 22);
                graphics2D.dispose();
            }
        };
    }

    private JPanel createStartPanel() {
        JPanel jPanel = this.createOverlayCard(new GridBagLayout());
        jPanel.setBorder(new EmptyBorder(22, 22, 22, 22));
        jPanel.setPreferredSize(new Dimension(320, 424));
        JPanel jPanel2 = new JPanel();
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        jPanel2.setOpaque(false);
        jPanel2.setPreferredSize(new Dimension(240, 286));
        JLabel jLabel = new JLabel("\uc6d4\uc57c\uace0\ub4f1\ud559\uad50");
        jLabel.setAlignmentX(0.5f);
        jLabel.setHorizontalAlignment(0);
        jLabel.setFont(new Font("Malgun Gothic", 1, 24));
        jLabel.setForeground(new Color(212, 218, 228));
        JLabel jLabel2 = new JLabel("\uc774\ub984");
        jLabel2.setAlignmentX(0.5f);
        jLabel2.setHorizontalAlignment(0);
        jLabel2.setFont(new Font("Malgun Gothic", 1, 13));
        jLabel2.setForeground(ACCENT);
        jLabel2.setBorder(new EmptyBorder(12, 0, 6, 0));
        this.nameField.setMaximumSize(new Dimension(240, 36));
        this.nameField.setPreferredSize(new Dimension(240, 36));
        this.nameField.setFont(new Font("Malgun Gothic", 0, 16));
        this.nameField.setHorizontalAlignment(0);
        this.nameField.setBackground(new Color(73, 80, 93));
        this.nameField.setForeground(new Color(232, 236, 242));
        this.nameField.setCaretColor(ACCENT);
        this.nameField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(110, 120, 138), 1), new EmptyBorder(8, 10, 8, 10)));
        ((AbstractDocument)this.nameField.getDocument()).setDocumentFilter(new LengthFilter(5));
        this.nameField.addActionListener(actionEvent -> this.startGame());
        JButton jButton = this.createMenuButton("\uc2dc\uc791", this::startGame);
        this.continueGameButton = this.createMenuButton("\uc774\uc5b4\ud558\uae30", this::continueGame);
        JButton jButton2 = this.createMenuButton("\uc5d4\ub529 \ubaa8\uc74c", this::showEndingGallery);
        JButton jButton3 = this.createMenuButton("\uae30\ub85d \uc0ad\uc81c", this::clearSaveData);
        this.continueGameButton.setEnabled(Files.exists(SAVE_PATH, new LinkOption[0]));
        jPanel2.add(jLabel);
        jPanel2.add(jLabel2);
        jPanel2.add(this.nameField);
        jPanel2.add(Box.createVerticalStrut(14));
        jPanel2.add(jButton);
        jPanel2.add(Box.createVerticalStrut(8));
        jPanel2.add(this.continueGameButton);
        jPanel2.add(Box.createVerticalStrut(8));
        jPanel2.add(jButton2);
        jPanel2.add(Box.createVerticalStrut(8));
        jPanel2.add(jButton3);
        jPanel.add(jPanel2);
        return jPanel;
    }

    private JPanel createGalleryPanel() {
        JPanel jPanel = this.createOverlayCard(new BorderLayout(0, 14));
        jPanel.setBorder(new EmptyBorder(22, 22, 22, 22));
        jPanel.setPreferredSize(new Dimension(320, 460));
        JLabel jLabel = new JLabel("\uc5d4\ub529 \ubaa8\uc74c");
        jLabel.setFont(new Font("Malgun Gothic", 1, 21));
        jLabel.setForeground(new Color(216, 221, 230));
        jLabel.setHorizontalAlignment(0);
        jPanel.add((Component)jLabel, "North");
        this.galleryListPanel.setLayout(new BoxLayout(this.galleryListPanel, 1));
        this.galleryListPanel.setOpaque(false);
        jPanel.add((Component)this.galleryListPanel, "Center");
        JButton jButton = this.createMenuButton("\ub3cc\uc544\uac00\uae30", this::showStartScreen);
        jPanel.add((Component)jButton, "South");
        return jPanel;
    }

    private JButton createMenuButton(String string, Runnable runnable) {
        final JButton jButton = new JButton(string);
        jButton.setAlignmentX(0.5f);
        jButton.setHorizontalAlignment(0);
        jButton.setFocusPainted(false);
        jButton.setFont(new Font("Malgun Gothic", 1, 15));
        jButton.setBackground(BUTTON);
        jButton.setForeground(new Color(233, 236, 242));
        jButton.setBorder(this.choiceBorder(new Color(108, 118, 136), new Color(255, 255, 255, 24)));
        jButton.setOpaque(true);
        jButton.setContentAreaFilled(true);
        jButton.setMaximumSize(new Dimension(240, 42));
        jButton.setPreferredSize(new Dimension(240, 42));
        jButton.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {
                jButton.setBackground(BUTTON_HOVER);
                jButton.setBorder(Main.this.choiceBorder(ACCENT, new Color(255, 255, 255, 32)));
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {
                jButton.setBackground(BUTTON);
                jButton.setBorder(Main.this.choiceBorder(new Color(108, 118, 136), new Color(255, 255, 255, 24)));
            }
        });
        jButton.addActionListener(actionEvent -> runnable.run());
        return jButton;
    }

    private JPanel createOverlayCard(LayoutManager layoutManager) {
        JPanel jPanel = new JPanel(layoutManager){

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D graphics2D = (Graphics2D)graphics.create();
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(new Color(0, 0, 0, 54));
                graphics2D.fillRoundRect(6, 8, this.getWidth() - 12, this.getHeight() - 8, 24, 24);
                graphics2D.setPaint(new GradientPaint(0.0f, 0.0f, new Color(44, 49, 60, 242), 0.0f, this.getHeight(), new Color(55, 61, 73, 238)));
                graphics2D.fillRoundRect(0, 0, this.getWidth(), this.getHeight(), 26, 26);
                graphics2D.setColor(new Color(192, 199, 211, 60));
                graphics2D.drawRoundRect(0, 0, this.getWidth() - 1, this.getHeight() - 1, 26, 26);
                graphics2D.setColor(new Color(108, 118, 136, 130));
                graphics2D.drawRoundRect(1, 1, this.getWidth() - 3, this.getHeight() - 3, 24, 24);
                graphics2D.dispose();
                super.paintComponent(graphics);
            }
        };
        jPanel.setOpaque(false);
        return jPanel;
    }

    private void initEndingMetadata() {
        this.endingTitles.put("ending_true", "\uae30\ub85d\ub41c \uc9c4\uc2e4");
        this.endingTitles.put("ending_wrong_accusation", "\ud76c\uc0dd\uc591");
        this.endingTitles.put("ending_silence", "\ubbf8\uc644\uc758 \ubcf4\uace0\uc11c");
        this.endingDescriptions.put("ending_true", "\ubb34\ub9ac\ud55c \ucd2c\uc601\uacfc \uc548\uc804 \ubd80\uc7ac\uac00 \ub9cc\ub4e0 \uc5f0\uc18d \uc0ac\uace0\ub97c \ubc1d\ud600\ub0b8 \uacb0\ub9d0");
        this.endingDescriptions.put("ending_wrong_accusation", "\uae40\ud604\uc9c4\uc5d0\uac8c \uc8c4\ub97c \ub4a4\uc9d1\uc5b4\uc50c\uc6b0\uace0 \uad6c\uc870\uc801 \uc6d0\uc778\uc744 \ub193\uce5c \uacb0\ub9d0");
        this.endingDescriptions.put("ending_silence", "\uc9c4\uc2e4 \uc9c1\uc804\uc5d0\uc11c \uba48\ucdb0 \ud504\ub85c\uc81d\ud2b8\uc758 \ucc45\uc784\ub9cc \ud750\ub824\uc9c4 \uacb0\ub9d0");
    }

    private void installKeyBindings() {
        JRootPane jRootPane = this.getRootPane();
        InputMap inputMap = jRootPane.getInputMap(2);
        ActionMap actionMap = jRootPane.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "advance-story");
        actionMap.put("advance-story", new AbstractAction(){

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                Main.this.advanceStory();
            }
        });
    }

    // scenes.json을 읽어 Scene 런타임 객체로 변환하는 시작점이다.
    private void initScenes() {
        this.scenes.clear();
        for (SceneDefinition sceneDefinition : this.loadSceneDefinitions()) {
            this.validateImageReference(sceneDefinition.id, "backgroundImage", sceneDefinition.backgroundImage);
            this.validateImageReference(sceneDefinition.id, "characterImage", sceneDefinition.characterImage);
            this.validatePageDefinitions(sceneDefinition);
            this.scenes.put(sceneDefinition.id, new Scene(sceneDefinition.title, sceneDefinition.chapter, sceneDefinition.narration, sceneDefinition.speaker, sceneDefinition.dialogue, sceneDefinition.backgroundImage, sceneDefinition.characterImage, this.buildPageSpecs(sceneDefinition.pages), this.buildChoices(sceneDefinition.choices), this.buildSceneOnEnter(sceneDefinition.onEnter)));
        }
    }

    // JSON 파일을 읽고, 최상위 장면 정의 목록으로 바꾼다.
    private List<SceneDefinition> loadSceneDefinitions() {
        try {
            String string = Files.readString(SCENES_PATH, StandardCharsets.UTF_8);
            Object object = new SimpleJsonParser(string).parse();
            List<Object> list = this.requireList(object, "scenes root");
            ArrayList<SceneDefinition> arrayList = new ArrayList<SceneDefinition>();
            for (Object object2 : list) {
                arrayList.add(this.parseSceneDefinition(this.requireMap(object2, "scene")));
            }
            return arrayList;
        }
        catch (IOException iOException) {
            throw new IllegalStateException("\uc7a5\uba74 JSON\uc744 \uc77d\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4: " + String.valueOf(SCENES_PATH), iOException);
        }
    }

    // 장면 하나를 파싱한다. pages/choices도 같이 읽어 둔다.
    private SceneDefinition parseSceneDefinition(Map<String, Object> map) {
        ArrayList<ChoiceDefinition> arrayList = new ArrayList<ChoiceDefinition>();
        for (Object object2 : this.requireList(map.get("choices"), "choices")) {
            arrayList.add(this.parseChoiceDefinition(this.requireMap(object2, "choice")));
        }
        ArrayList<PageDefinition> arrayList2 = new ArrayList<PageDefinition>();
        Object object2 = map.get("pages");
        if (object2 instanceof List) {
            List list = (List)object2;
            for (Object e : list) {
                arrayList2.add(this.parsePageDefinition(this.requireMap(e, "page")));
            }
        }
        return new SceneDefinition(this.readRequiredString(map, "id"), this.readRequiredString(map, "title"), this.readRequiredString(map, "chapter"), this.readRequiredString(map, "narration"), this.readRequiredString(map, "speaker"), this.readRequiredString(map, "dialogue"), this.readRequiredString(map, "backgroundImage"), this.readRequiredString(map, "characterImage"), this.readOptionalString(map, "onEnter"), arrayList2, arrayList);
    }

    private PageDefinition parsePageDefinition(Map<String, Object> map) {
        return new PageDefinition(this.readRequiredString(map, "text"), this.readOptionalString(map, "speaker"), this.readOptionalString(map, "backgroundImage"), this.readOptionalString(map, "characterImage"));
    }

    private ChoiceDefinition parseChoiceDefinition(Map<String, Object> map) {
        return new ChoiceDefinition(this.readRequiredString(map, "label"), this.readRequiredString(map, "nextSceneId"), this.readOptionalString(map, "effect"), this.readOptionalString(map, "visibility"), this.readBoolean(map, "recordsSuspicion"));
    }

    private List<Choice> buildChoices(List<ChoiceDefinition> list) {
        ArrayList<Choice> arrayList = new ArrayList<Choice>();
        for (ChoiceDefinition choiceDefinition : list) {
            arrayList.add(new Choice(choiceDefinition.label, choiceDefinition.nextSceneId, this.buildChoiceEffect(choiceDefinition.effect), this.buildChoiceVisibility(choiceDefinition.visibility), choiceDefinition.recordsSuspicion));
        }
        return List.copyOf(arrayList);
    }

    // JSON에 적힌 onEnter 문자열 ID를 실제 상태 변경 함수로 연결한다.
    private Consumer<GameState> buildSceneOnEnter(String string) {
        return switch (string == null ? DEFAULT_PLAYER_NAME : string) {
            case DEFAULT_PLAYER_NAME, "noop" -> null;
            case "unlock_music_record" -> gameState -> gameState.unlockClue("\uc74c\uc545\uc2e4 \ub179\uc74c");
            case "unlock_music_witness" -> gameState -> gameState.unlockClue("\ubcf5\ub3c4 \ubaa9\uaca9\ub2f4");
            case "unlock_music_sheet" -> gameState -> gameState.unlockClue("\ucc22\uc5b4\uc9c4 \uc545\ubcf4");
            case "unlock_science_label" -> gameState -> gameState.unlockClue("\ub77c\ubca8 \uc9c0\uc6cc\uc9c4 \uc6a9\uae30");
            case "unlock_science_cleanup" -> gameState -> gameState.unlockClue("\uc815\ub9ac \uc9c0\uc2dc \uae30\ub85d");
            case "unlock_science_warning" -> gameState -> gameState.unlockClue("\uc190\uae00\uc528 \uacbd\uace0 \uba54\ubaa8");
            case "unlock_pool_video" -> gameState -> gameState.unlockClue("\uc218\uc601\uc7a5 \uc601\uc0c1");
            case "unlock_pool_support" -> gameState -> gameState.unlockClue("\ud604\uc7a5 \uc9c0\uc6d0 \uae30\ub85d");
            case "unlock_pool_facility" -> gameState -> gameState.unlockClue("\uc218\uc601\uc7a5 \uc2dc\uc124 \uc810\uac80\ud45c");
            case "unlock_rooftop_gate" -> gameState -> gameState.unlockClue("\uc625\uc0c1 \ucd9c\uc785 \uae30\ub85d");
            case "unlock_rooftop_prejudice" -> gameState -> gameState.unlockClue("\uc758\uc2ec\uc774 \ud37c\uc9c4 \uc99d\uc5b8");
            case "unlock_project_minutes" -> gameState -> gameState.unlockClue("\ud504\ub85c\uc81d\ud2b8 \ud68c\uc758\ub85d");
            default -> throw new IllegalArgumentException("\uc54c \uc218 \uc5c6\ub294 scene onEnter ID: " + string);
        };
    }

    // 선택지 effect ID를 실제 분기/점수/단서 변경 로직으로 바꾼다.
    private Consumer<GameState> buildChoiceEffect(String string) {
        return switch (string == null || string.isBlank() ? "noop" : string) {
            case "noop" -> gameState -> {};
            case "mark_music_wrong" -> gameState -> {
                ++gameState.suspicionScore;
                gameState.musicSolved = true;
            };
            case "resolve_music_true" -> gameState -> {
                gameState.musicSolved = true;
                ++gameState.truthScore;
                gameState.unlockClue("\uc8fc\ub2e4\uc601 \uc0ac\uac74 \ud574\uacb0");
            };
            case "mark_science_wrong" -> gameState -> {
                ++gameState.suspicionScore;
                gameState.scienceSolved = true;
            };
            case "resolve_science_true" -> gameState -> {
                gameState.scienceSolved = true;
                ++gameState.truthScore;
                gameState.unlockClue("\ud55c\uc2b9\uc900 \uc0ac\uac74 \ud574\uacb0");
            };
            case "mark_pool_wrong" -> gameState -> {
                ++gameState.suspicionScore;
                gameState.poolSolved = true;
            };
            case "resolve_pool_true" -> gameState -> {
                gameState.poolSolved = true;
                ++gameState.truthScore;
                gameState.unlockClue("\uae40\uc900\uc601 \uc0ac\uac74 \ud574\uacb0");
            };
            case "set_final_accuse" -> gameState -> {
                gameState.finalChoice = "accuse";
            };
            case "set_final_silence" -> gameState -> {
                gameState.finalChoice = "silence";
            };
            case "set_final_innocent" -> gameState -> {
                gameState.finalChoice = "innocent";
            };
            case "reset_state" -> GameState::reset;
            default -> throw new IllegalArgumentException("\uc54c \uc218 \uc5c6\ub294 choice effect ID: " + string);
        };
    }

    // 선택지 visibility ID를 실제 표시 조건 함수로 바꾼다.
    private Predicate<GameState> buildChoiceVisibility(String string) {
        return switch (string == null || string.isBlank() ? "always" : string) {
            case "always" -> gameState -> true;
            case "can_review_music" -> Main::canReviewMusic;
            case "can_review_science" -> Main::canReviewScience;
            case "can_review_pool" -> Main::canReviewPool;
            case "can_enter_music_case" -> gameState -> !gameState.musicSolved;
            case "can_enter_science_case" -> gameState -> gameState.musicSolved && !gameState.scienceSolved;
            case "can_enter_pool_case" -> gameState -> gameState.musicSolved && gameState.scienceSolved && !gameState.poolSolved;
            case "can_enter_final_case" -> gameState -> gameState.musicSolved && gameState.scienceSolved && gameState.poolSolved && !gameState.visitedScenes.contains("rooftop_intro");
            case "can_review_final_board" -> Main::canReviewFinalBoard;
            default -> throw new IllegalArgumentException("\uc54c \uc218 \uc5c6\ub294 choice visibility ID: " + string);
        };
    }

    private void validateImageReference(String string, String string2, String string3) {
        if (string3 == null || string3.isBlank()) {
            return;
        }
        Path path = Path.of("assets", "images", string3);
        if (!Files.exists(path, new LinkOption[0])) {
            throw new IllegalStateException("\uc7a5\uba74 '" + string + "'\uc758 " + string2 + " \ud30c\uc77c\uc774 \uc5c6\uc2b5\ub2c8\ub2e4: " + String.valueOf(path));
        }
    }

    private String readRequiredString(Map<String, Object> map, String string) {
        Object object = map.get(string);
        if (!(object instanceof String)) {
            throw new IllegalArgumentException("\ubb38\uc790\uc5f4 \ud544\ub4dc\uac00 \ud544\uc694\ud569\ub2c8\ub2e4: " + string);
        }
        String string2 = (String)object;
        return string2;
    }

    private String readOptionalString(Map<String, Object> map, String string) {
        Object object = map.get(string);
        if (object == null) {
            return DEFAULT_PLAYER_NAME;
        }
        if (object instanceof String) {
            String string2 = (String)object;
            return string2;
        }
        throw new IllegalArgumentException("\ubb38\uc790\uc5f4 \ud544\ub4dc\uac00 \ud544\uc694\ud569\ub2c8\ub2e4: " + string);
    }

    private boolean readBoolean(Map<String, Object> map, String string) {
        Object object = map.get(string);
        if (object instanceof Boolean) {
            Boolean bl = (Boolean)object;
            return bl;
        }
        throw new IllegalArgumentException("\ubd88\ub9ac\uc5b8 \ud544\ub4dc\uac00 \ud544\uc694\ud569\ub2c8\ub2e4: " + string);
    }

    private List<PageSpec> buildPageSpecs(List<PageDefinition> list) {
        ArrayList<PageSpec> arrayList = new ArrayList<PageSpec>();
        for (PageDefinition pageDefinition : list) {
            arrayList.add(new PageSpec(pageDefinition.text, pageDefinition.speaker, pageDefinition.backgroundImage, pageDefinition.characterImage));
        }
        return List.copyOf(arrayList);
    }

    private void validatePageDefinitions(SceneDefinition sceneDefinition) {
        for (int i = 0; i < sceneDefinition.pages.size(); ++i) {
            PageDefinition pageDefinition = sceneDefinition.pages.get(i);
            if (pageDefinition.backgroundImage != null && !pageDefinition.backgroundImage.isBlank()) {
                this.validateImageReference(sceneDefinition.id + "[page " + i + "]", "backgroundImage", pageDefinition.backgroundImage);
            }
            if (pageDefinition.characterImage == null || pageDefinition.characterImage.isBlank()) continue;
            this.validateImageReference(sceneDefinition.id + "[page " + i + "]", "characterImage", pageDefinition.characterImage);
        }
    }

    private List<Object> requireList(Object object, String string) {
        if (object instanceof List) {
            List list = (List)object;
            return new ArrayList<Object>(list);
        }
        throw new IllegalArgumentException(string + " must be a JSON array");
    }

    private Map<String, Object> requireMap(Object object, String string) {
        if (object instanceof Map) {
            Map map = (Map)object;
            LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>)map).entrySet()) {
                linkedHashMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return linkedHashMap;
        }
        throw new IllegalArgumentException(string + " must be a JSON object");
    }

    private void resetState() {
        this.state.reset();
    }

    private void showStartScreen() {
        this.currentScene = null;
        this.currentSceneId = "start_menu";
        this.activeBackgroundImage = "bg_school_gate_night.png";
        this.activeCharacterImage = DEFAULT_PLAYER_NAME;
        this.titleLabel.setText("\uc6d4\uc57c\uace0\ub4f1\ud559\uad50");
        this.chapterLabel.setText("START");
        this.nameField.setText(this.state.playerName == null || this.state.playerName.isBlank() ? DEFAULT_PLAYER_NAME : this.state.playerName);
        this.storyArea.setText(DEFAULT_PLAYER_NAME);
        this.speakerBadge.setVisible(false);
        this.speakerPanel.setVisible(false);
        this.choiceOverlay.setVisible(false);
        this.galleryOverlay.setVisible(false);
        this.startOverlay.setVisible(true);
        this.hudPanel.setVisible(false);
        this.refreshContinueButton();
        this.layoutSceneLayers();
        this.refreshImages();
        SwingUtilities.invokeLater(() -> this.nameField.requestFocusInWindow());
    }

    private void startGame() {
        String string;
        String string2 = string = this.nameField.getText() == null ? DEFAULT_PLAYER_NAME : this.nameField.getText().trim();
        if (string.isEmpty()) {
            this.nameField.requestFocusInWindow();
            return;
        }
        this.state.playerName = string;
        this.state.resetForNewRun();
        this.startOverlay.setVisible(false);
        this.galleryOverlay.setVisible(false);
        this.hudPanel.setVisible(true);
        this.showScene("prologue_arrival");
    }

    private void continueGame() {
        SaveData saveData = SaveData.load(SAVE_PATH);
        if (saveData == null) {
            this.refreshContinueButton();
            return;
        }
        saveData.applyTo(this.state);
        this.textSpeedMs = saveData.textSpeedMs;
        this.nameField.setText(this.state.playerName);
        this.startOverlay.setVisible(false);
        this.galleryOverlay.setVisible(false);
        this.hudPanel.setVisible(true);
        this.showScene(saveData.currentSceneId == null || saveData.currentSceneId.isBlank() ? "prologue_arrival" : saveData.currentSceneId);
    }

    private void clearSaveData() {
        try {
            Files.deleteIfExists(SAVE_PATH);
        }
        catch (IOException iOException) {
            // empty catch block
        }
        this.state.reset();
        this.showStartScreen();
    }

    private void refreshContinueButton() {
        if (this.continueGameButton != null) {
            this.continueGameButton.setEnabled(Files.exists(SAVE_PATH, new LinkOption[0]));
        }
    }

    private void persistState() {
        SaveData.from(this.state, this.currentSceneId, this.textSpeedMs).save(SAVE_PATH);
        this.refreshContinueButton();
    }

    private void loadSaveData() {
        SaveData saveData = SaveData.load(SAVE_PATH);
        if (saveData == null) {
            this.refreshContinueButton();
            return;
        }
        saveData.applyTo(this.state);
        this.textSpeedMs = saveData.textSpeedMs;
        this.refreshContinueButton();
    }

    private void showEndingGallery() {
        this.refreshEndingGallery();
        this.startOverlay.setVisible(false);
        this.galleryOverlay.setVisible(true);
        this.hudPanel.setVisible(false);
        this.speakerPanel.setVisible(false);
        this.choiceOverlay.setVisible(false);
        this.currentScene = null;
        this.currentSceneId = "ending_gallery";
        this.activeBackgroundImage = "bg_archive_room.png";
        this.activeCharacterImage = DEFAULT_PLAYER_NAME;
        this.titleLabel.setText("\uc5d4\ub529 \ubaa8\uc74c");
        this.chapterLabel.setText("ARCHIVE");
        this.layoutSceneLayers();
        this.refreshImages();
    }

    private void refreshEndingGallery() {
        this.galleryListPanel.removeAll();
        for (String string : this.endingTitles.keySet()) {
            boolean bl = this.state.seenEndings.contains(string);
            JPanel jPanel = new JPanel();
            jPanel.setLayout(new BoxLayout(jPanel, 1));
            jPanel.setOpaque(true);
            jPanel.setBackground(new Color(70, 77, 90));
            jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(111, 121, 139), 1), new EmptyBorder(12, 12, 12, 12)));
            JLabel jLabel = new JLabel(bl ? this.applyPlayerName(this.endingTitles.get(string)) : "???");
            jLabel.setFont(new Font("Malgun Gothic", 1, 15));
            jLabel.setForeground(bl ? new Color(224, 229, 236) : ACCENT_SOFT);
            JLabel jLabel2 = new JLabel(bl ? this.applyPlayerName(this.endingDescriptions.get(string)) : "\uc544\uc9c1 \ud655\uc778\ud558\uc9c0 \ubabb\ud55c \uc5d4\ub529");
            jLabel2.setFont(new Font("Malgun Gothic", 0, 12));
            jLabel2.setForeground(new Color(181, 188, 199));
            jPanel.add(jLabel);
            jPanel.add(Box.createVerticalStrut(4));
            jPanel.add(jLabel2);
            this.galleryListPanel.add(jPanel);
            this.galleryListPanel.add(Box.createVerticalStrut(10));
        }
        this.galleryListPanel.revalidate();
        this.galleryListPanel.repaint();
    }

    private void showScene(String string) {
        Scene scene = this.scenes.get(string);
        if (scene == null) {
            return;
        }
        this.currentSceneId = string;
        this.currentScene = scene;
        this.state.visitedScenes.add(string);
        if (scene.onEnter != null) {
            scene.onEnter.accept(this.state);
        }
        this.titleLabel.setText(this.applyPlayerName(scene.title));
        this.chapterLabel.setText(this.applyPlayerName(scene.chapter));
        this.activeBackgroundImage = scene.backgroundImage;
        this.activeCharacterImage = scene.characterImage;
        this.startOverlay.setVisible(false);
        this.galleryOverlay.setVisible(false);
        this.hudPanel.setVisible(true);
        if (this.isEndingSceneId(string)) {
            this.state.seenEndings.add(string);
        }
        this.prepareSceneFlow(scene);
        this.persistState();
        this.layoutSceneLayers();
        this.refreshImages();
        SwingUtilities.invokeLater(() -> {
            this.layoutSceneLayers();
            this.refreshImages();
            this.scenePanel.repaint();
        });
    }

    private JButton createChoiceButton(Choice choice) {
        final boolean bl = this.isChoiceCompleted(choice);
        final boolean bl2 = choice.isVisible(this.state) && !bl;
        String string = this.applyPlayerName(choice.label);
        final JButton jButton = new JButton(this.asWrappedHtml(string, 26));
        jButton.setHorizontalAlignment(0);
        jButton.setFocusPainted(false);
        jButton.setFont(new Font("Malgun Gothic", 1, 14));
        jButton.setPreferredSize(new Dimension(248, 64));
        jButton.setBackground(bl2 ? BUTTON : (bl ? new Color(76, 83, 96) : new Color(58, 62, 71)));
        jButton.setForeground(bl2 ? new Color(233, 236, 242) : (bl ? new Color(206, 214, 226) : new Color(148, 154, 166)));
        jButton.setBorder(this.choiceBorder(bl2 ? new Color(108, 118, 136) : (bl ? new Color(118, 128, 146) : new Color(86, 92, 104)), bl2 ? new Color(255, 255, 255, 24) : (bl ? new Color(255, 255, 255, 18) : new Color(255, 255, 255, 10))));
        jButton.setOpaque(true);
        jButton.setContentAreaFilled(true);
        jButton.setEnabled(bl2);
        jButton.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {
                if (!jButton.isEnabled()) {
                    return;
                }
                jButton.setBackground(BUTTON_HOVER);
                jButton.setBorder(Main.this.choiceBorder(ACCENT, new Color(255, 255, 255, 32)));
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {
                jButton.setBackground(bl2 ? BUTTON : (bl ? new Color(76, 83, 96) : new Color(58, 62, 71)));
                jButton.setBorder(Main.this.choiceBorder(bl2 ? new Color(108, 118, 136) : (bl ? new Color(118, 128, 146) : new Color(86, 92, 104)), bl2 ? new Color(255, 255, 255, 24) : (bl ? new Color(255, 255, 255, 18) : new Color(255, 255, 255, 10))));
            }
        });
        jButton.addActionListener(actionEvent -> {
            if (!jButton.isEnabled()) {
                return;
            }
            this.performChoice(choice);
        });
        return jButton;
    }

    private boolean isChoiceCompleted(Choice choice) {
        return switch (choice.nextSceneId) {
            case "pool_intro" -> this.state.poolSolved;
            case "music_intro" -> this.state.musicSolved;
            case "science_intro" -> this.state.scienceSolved;
            case "pool_video", "pool_interview", "pool_facility_log", "pool_review", "music_record", "music_corridor_witness", "music_torn_sheet", "music_review", "science_lab", "science_cleanup_record", "science_handwritten_note", "science_review", "rooftop_gate", "rooftop_prejudice", "rooftop_note", "pool_reconstruction", "music_reconstruction", "science_reconstruction", "final_verdict" -> this.state.visitedScenes.contains(choice.nextSceneId);
            case "rooftop_intro" -> this.state.visitedScenes.contains("rooftop_intro");
            case "final_board" -> this.state.visitedScenes.contains("final_board");
            default -> false;
        };
    }

    private Border choiceBorder(Color color, Color color2) {
        return BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color, 1), BorderFactory.createLineBorder(color2, 1)), new EmptyBorder(12, 16, 12, 16));
    }

    private String buildStatusText() {
        return "truth=" + this.state.truthScore + "  suspect=" + this.state.suspicionScore + "  clues=" + this.state.clues.size() + "  solved=" + this.solvedCount() + "/3";
    }

    private int solvedCount() {
        int n = 0;
        if (this.state.poolSolved) {
            ++n;
        }
        if (this.state.musicSolved) {
            ++n;
        }
        if (this.state.scienceSolved) {
            ++n;
        }
        return n;
    }

    private static boolean hasVisitedAll(GameState gameState, String ... stringArray) {
        for (String string : stringArray) {
            if (gameState.visitedScenes.contains(string)) continue;
            return false;
        }
        return true;
    }

    private static boolean canReviewPool(GameState gameState) {
        return Main.hasVisitedAll(gameState, "pool_video", "pool_interview", "pool_facility_log");
    }

    private static boolean canReviewMusic(GameState gameState) {
        return Main.hasVisitedAll(gameState, "music_record", "music_corridor_witness", "music_torn_sheet");
    }

    private static boolean canReviewScience(GameState gameState) {
        return Main.hasVisitedAll(gameState, "science_lab", "science_cleanup_record", "science_handwritten_note");
    }

    private static boolean canReviewFinalBoard(GameState gameState) {
        return Main.hasVisitedAll(gameState, "rooftop_gate", "rooftop_prejudice", "rooftop_note");
    }

    // 현재 페이지 문장을 타이핑 효과로 보여준다.
    // 사용자가 중간에 클릭하면 revealCurrentPageImmediately()가 나머지를 즉시 채운다.
    private void startDialogueAnimation(String string) {
        if (this.dialogueTimer != null && this.dialogueTimer.isRunning()) {
            this.dialogueTimer.stop();
        }
        this.storyArea.setText(DEFAULT_PLAYER_NAME);
        this.pageFullyVisible = false;
        if (string == null || string.isEmpty()) {
            this.pageFullyVisible = true;
            return;
        }
        int[] nArray = new int[]{0};
        this.dialogueTimer = new Timer(this.textSpeedMs, actionEvent -> {
            nArray[0] = nArray[0] + 1;
            this.storyArea.setText(string.substring(0, nArray[0]));
            if (nArray[0] >= string.length()) {
                this.pageFullyVisible = true;
                ((Timer)actionEvent.getSource()).stop();
            }
        });
        this.dialogueTimer.setInitialDelay(70);
        this.dialogueTimer.start();
    }

    private void enforceMobileAspectRatio() {
        if (this.adjustingFrame) {
            return;
        }
        int n = this.getWidth();
        int n2 = this.getHeight();
        int n3 = this.getMinimumSize().width;
        int n4 = this.getMinimumSize().height;
        int n5 = Math.abs(n - this.lastFrameSize.width);
        int n6 = Math.abs(n2 - this.lastFrameSize.height);
        int n7 = n;
        int n8 = n2;
        if (n5 >= n6) {
            n7 = Math.max(n3, n);
            n8 = Math.max(n4, (int)Math.round((double)n7 / 0.5625));
        } else {
            n8 = Math.max(n4, n2);
            n7 = Math.max(n3, (int)Math.round((double)n8 * 0.5625));
        }
        if (this.getWidth() == n7 && this.getHeight() == n8) {
            this.lastFrameSize = new Dimension(n7, n8);
            return;
        }
        this.adjustingFrame = true;
        this.setSize(n7, n8);
        this.adjustingFrame = false;
        this.lastFrameSize = new Dimension(n7, n8);
    }

    // 창 크기에 맞춰 배경, 캐릭터, 텍스트 카드, 선택지 위치를 다시 계산한다.
    // UI가 어긋나면 먼저 이 좌표 계산이 맞는지 확인하면 된다.
    private void layoutSceneLayers() {
        int n = Math.max(1, this.scenePanel.getWidth());
        int n2 = Math.max(1, this.scenePanel.getHeight());
        this.backgroundLabel.setBounds(0, 0, n, n2);
        int n3 = Math.max(1, n - 36);
        int n4 = 210;
        int n5 = Math.max(0, n2 - n4);
        int n6 = 20;
        int n7 = Math.max(n6 + 80, n5 + 48);
        int n8 = Math.max(1, n7 - n6);
        int n9 = Math.max(1, (int)Math.round((double)n * 0.96));
        int n10 = n8;
        int n11 = (n - n9) / 2;
        int n12 = n6;
        this.characterLabel.setBounds(n11, n12, n9, n10);
        this.overlayPanel.setBounds(0, 0, n, n2);
        this.hudPanel.setBounds(0, n5, n, n4);
        this.storyPanel.setBounds(18, 0, n3, 196);
        int n13 = Math.min(Math.max(120, this.speakerBadge.getPreferredSize().width), Math.max(120, n3 - 28));
        int n14 = 32;
        int n15 = Math.max(12, n5 - 42 + 10);
        this.speakerPanel.setBounds(n14, n15, n13, 42);
        this.storyArea.setBounds(0, 6, n3, this.getStoryAreaHeight());
        this.startOverlay.setBounds(0, 0, n, n2);
        this.galleryOverlay.setBounds(0, 0, n, n2);
        int n16 = this.speakerPanel.isVisible() ? Math.max(12, n15 + 42 + 8) : 12;
        int n17 = Math.max(n16 + 40, n5 - 20);
        int n18 = Math.max(1, n17 - n16);
        Dimension dimension = this.choicesPanel.getPreferredSize();
        int n19 = Math.max(18, (n - dimension.width) / 2);
        int n20 = n16 + Math.max(0, (n18 - dimension.height) / 2);
        this.choicesPanel.setBounds(n19, n20, dimension.width, dimension.height);
        this.scenePanel.setComponentZOrder(this.backgroundLabel, 6);
        this.scenePanel.setComponentZOrder(this.characterLabel, 5);
        this.scenePanel.setComponentZOrder(this.speakerPanel, 4);
        this.scenePanel.setComponentZOrder(this.overlayPanel, 3);
        this.scenePanel.setComponentZOrder(this.hudPanel, 2);
        this.scenePanel.setComponentZOrder(this.galleryOverlay, 1);
        this.scenePanel.setComponentZOrder(this.startOverlay, 0);
    }

    private int getStoryAreaHeight() {
        return 196 - 12;
    }

    private void refreshImages() {
        int n = Math.max(1, this.scenePanel.getWidth());
        int n2 = Math.max(1, this.scenePanel.getHeight());
        String string = this.activeBackgroundImage == null ? DEFAULT_PLAYER_NAME : this.activeBackgroundImage;
        String string2 = this.activeCharacterImage == null ? DEFAULT_PLAYER_NAME : this.activeCharacterImage;
        this.setImage(this.backgroundLabel, string, n, n2, (String)(string.isEmpty() ? DEFAULT_PLAYER_NAME : "BG: " + string), true);
        int n3 = Math.max(0, n2 - 210);
        int n4 = Math.max(1, n3 + 48 - 20);
        this.setImage(this.characterLabel, string2, (int)Math.round((double)n * 0.96), n4, (String)(string2.isEmpty() ? DEFAULT_PLAYER_NAME : "CH: " + string2), false);
    }

    private void setImage(JLabel jLabel, String string, int n, int n2, String string2, boolean bl) {
        if (string == null || string.isEmpty()) {
            jLabel.setIcon(null);
            jLabel.setText(string2 == null ? DEFAULT_PLAYER_NAME : string2);
            return;
        }
        try {
            BufferedImage bufferedImage = this.loadImageCached(string);
            if (bufferedImage == null) {
                jLabel.setIcon(null);
                jLabel.setText(string2);
                return;
            }
            if (!bl) {
                bufferedImage = this.trimTransparentEdges(bufferedImage);
            }
            int n3 = bufferedImage.getWidth();
            int n4 = bufferedImage.getHeight();
            double d = bl ? Math.max((double)n / (double)n3, (double)n2 / (double)n4) : Math.min((double)n / (double)n3, (double)n2 / (double)n4);
            int n5 = Math.max(1, (int)Math.round((double)n3 * d));
            int n6 = Math.max(1, (int)Math.round((double)n4 * d));
            Image image = bufferedImage.getScaledInstance(n5, n6, 4);
            jLabel.setIcon(new ImageIcon(image));
            jLabel.setText(DEFAULT_PLAYER_NAME);
        }
        catch (IOException iOException) {
            jLabel.setIcon(null);
            jLabel.setText(string2);
        }
    }

    private BufferedImage trimTransparentEdges(BufferedImage bufferedImage) {
        int n = bufferedImage.getWidth();
        int n2 = bufferedImage.getHeight();
        int n3 = -1;
        int n4 = -1;
        for (int i = 0; i < bufferedImage.getHeight(); ++i) {
            for (int j = 0; j < bufferedImage.getWidth(); ++j) {
                int n5 = bufferedImage.getRGB(j, i) >>> 24 & 0xFF;
                if (n5 <= 8) continue;
                if (j < n) {
                    n = j;
                }
                if (i < n2) {
                    n2 = i;
                }
                if (j > n3) {
                    n3 = j;
                }
                if (i <= n4) continue;
                n4 = i;
            }
        }
        if (n3 < n || n4 < n2) {
            return bufferedImage;
        }
        return bufferedImage.getSubimage(n, n2, n3 - n + 1, n4 - n2 + 1);
    }

    private BufferedImage loadImageCached(String string) throws IOException {
        BufferedImage bufferedImage = this.imageCache.get(string);
        if (bufferedImage != null) {
            return bufferedImage;
        }
        String string2 = "assets/images/" + string;
        BufferedImage bufferedImage2 = ImageIO.read(new File(string2));
        if (bufferedImage2 != null) {
            this.imageCache.put(string, bufferedImage2);
        }
        return bufferedImage2;
    }

    private void prepareSceneFlow(Scene scene) {
        this.visibleChoices = this.collectVisibleChoices(scene);
        this.scenePages = this.buildPages(this.currentSceneId, scene);
        this.currentPageIndex = 0;
        this.choiceOverlay.setVisible(false);
        this.choicesPanel.removeAll();
        if (this.scenePages.isEmpty()) {
            this.storyArea.setText(DEFAULT_PLAYER_NAME);
            this.pageFullyVisible = true;
            this.handleSceneEnd();
            return;
        }
        this.showCurrentPage();
    }

    private List<Choice> collectVisibleChoices(Scene scene) {
        ArrayList<Choice> arrayList = new ArrayList<Choice>();
        for (Choice choice : scene.choices) {
            if (!choice.isVisible(this.state)) continue;
            arrayList.add(choice);
        }
        return arrayList;
    }

    // 장면 하나를 "클릭 한 번당 보여줄 페이지" 목록으로 펼친다.
    // JSON의 pages가 있으면 그 값을 우선 사용하고, 없으면 narration/dialogue를 자동 분할한다.
    private List<PageEntry> buildPages(String string, Scene scene) {
        ArrayList<PageEntry> arrayList = new ArrayList<PageEntry>();
        if (!scene.pages.isEmpty()) {
            for (PageSpec pageSpec : scene.pages) {
                String string2 = pageSpec.backgroundImage == null || pageSpec.backgroundImage.isBlank() ? scene.backgroundImage : pageSpec.backgroundImage;
                String string3 = pageSpec.speaker == null ? DEFAULT_PLAYER_NAME : pageSpec.speaker.strip();
                String string4 = this.resolvePageCharacterImage(pageSpec.text, string3, pageSpec.characterImage);
                arrayList.addAll(this.splitIntoPages(pageSpec.text, string3, string2, string4));
            }
            return arrayList;
        }
        if (scene.narration != null && !scene.narration.isBlank()) {
            arrayList.addAll(this.splitIntoPages(scene.narration, DEFAULT_PLAYER_NAME, scene.backgroundImage, DEFAULT_PLAYER_NAME));
        }
        if (scene.dialogue != null && !scene.dialogue.isBlank()) {
            arrayList.addAll(this.splitIntoPages(scene.dialogue, scene.speaker, scene.backgroundImage, scene.characterImage));
        }
        return arrayList;
    }

    private String resolvePageCharacterImage(String string, String string2, String string3) {
        if (string3 != null && !string3.isBlank()) {
            return string3;
        }
        if (string2 != null && !string2.isBlank()) {
            return this.characterImageForSpeaker(string2);
        }
        return this.inferNarrationCharacterImage(string);
    }

    private String characterImageForSpeaker(String string) {
        return switch (string == null ? DEFAULT_PLAYER_NAME : string.strip()) {
            case "\ud50c\ub808\uc774\uc5b4" -> "ch_player.png";
            case "\uad50\uc7a5" -> "ch_principal.png";
            case "\uc591\uc9c0\uc601" -> "ch_yang_jiyeong.png";
            case "\uae40\ud604\uc9c4", "\ud604\uc9c4" -> "ch_kim_hyeonjin.png";
            case "\uc8fc\ub2e4\uc601", "\ub2e4\uc601" -> "ch_ju_dayeong.png";
            case "\ud55c\uc2b9\uc900", "\uc2b9\uc900" -> "ch_han_seungjun.png";
            case "\uae40\uc900\uc601", "\uc900\uc601" -> "ch_kim_junyeong.png";
            case "\uae40\ud0dc\ud615", "\ud0dc\ud615" -> "ch_kim_taehyeong.png";
            default -> DEFAULT_PLAYER_NAME;
        };
    }

    private String inferNarrationCharacterImage(String string) {
        if (string == null || string.isBlank()) {
            return DEFAULT_PLAYER_NAME;
        }
        String string2 = this.applyPlayerName(string);
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<String, String>();
        this.collectNarrationMention(linkedHashMap, string2, "\uc591\uc9c0\uc601", "ch_yang_jiyeong.png");
        this.collectNarrationMention(linkedHashMap, string2, "\uae40\ud604\uc9c4", "ch_kim_hyeonjin.png");
        this.collectNarrationMention(linkedHashMap, string2, "\ud604\uc9c4", "ch_kim_hyeonjin.png");
        this.collectNarrationMention(linkedHashMap, string2, "\uc8fc\ub2e4\uc601", "ch_ju_dayeong.png");
        this.collectNarrationMention(linkedHashMap, string2, "\ub2e4\uc601", "ch_ju_dayeong.png");
        this.collectNarrationMention(linkedHashMap, string2, "\ud55c\uc2b9\uc900", "ch_han_seungjun.png");
        this.collectNarrationMention(linkedHashMap, string2, "\uc2b9\uc900", "ch_han_seungjun.png");
        this.collectNarrationMention(linkedHashMap, string2, "\uae40\uc900\uc601", "ch_kim_junyeong.png");
        this.collectNarrationMention(linkedHashMap, string2, "\uc900\uc601", "ch_kim_junyeong.png");
        this.collectNarrationMention(linkedHashMap, string2, "\uae40\ud0dc\ud615", "ch_kim_taehyeong.png");
        this.collectNarrationMention(linkedHashMap, string2, "\ud0dc\ud615", "ch_kim_taehyeong.png");
        this.collectNarrationMention(linkedHashMap, string2, "\uad50\uc7a5", "ch_principal.png");
        if (linkedHashMap.size() == 1) {
            return (String)linkedHashMap.values().iterator().next();
        }
        return DEFAULT_PLAYER_NAME;
    }

    private void collectNarrationMention(Map<String, String> map, String string, String string2, String string3) {
        if (string.contains(string2)) {
            map.put(string3, string3);
        }
    }

    // 실제 텍스트 카드에 들어갈 분량만큼 문자열을 잘라 PageEntry 여러 개로 만든다.
    // 이 단계가 잘못되면 마지막 줄이 카드 하단에서 잘려 보이기 쉽다.
    private List<PageEntry> splitIntoPages(String string, String string2, String string3, String string4) {
        ArrayList<PageEntry> arrayList = new ArrayList<PageEntry>();
        ArrayList<String> arrayList2 = new ArrayList<String>();
        Matcher matcher = SENTENCE_PATTERN.matcher(this.applyPlayerName(string).strip());
        while (matcher.find()) {
            String string5 = matcher.group().trim();
            if (string5.isEmpty()) continue;
            arrayList2.add(string5);
        }
        if (arrayList2.isEmpty()) {
            return arrayList;
        }
        int n = Math.max(1, this.getStoryTextLineCapacity());
        StringBuilder stringBuilder = new StringBuilder();
        for (String string6 : arrayList2) {
            String string7;
            String string8 = string7 = stringBuilder.length() > 0 ? String.valueOf(stringBuilder) + " " + string6 : string6;
            if (this.countWrappedLines(string7) <= n) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(' ');
                }
                stringBuilder.append(string6);
                continue;
            }
            if (stringBuilder.length() > 0) {
                arrayList.add(new PageEntry(stringBuilder.toString(), string2 == null ? DEFAULT_PLAYER_NAME : string2.strip(), string3, string4));
                stringBuilder.setLength(0);
            }
            List<String> list = this.splitOversizeText(string6, n);
            for (int i = 0; i < list.size() - 1; ++i) {
                arrayList.add(new PageEntry(list.get(i), string2 == null ? DEFAULT_PLAYER_NAME : string2.strip(), string3, string4));
            }
            if (list.isEmpty()) continue;
            stringBuilder.append(list.get(list.size() - 1));
        }
        if (stringBuilder.length() > 0) {
            arrayList.add(new PageEntry(stringBuilder.toString(), string2 == null ? DEFAULT_PLAYER_NAME : string2.strip(), string3, string4));
        }
        return arrayList;
    }

    private int getStoryTextLineCapacity() {
        FontMetrics fontMetrics = this.storyArea.getFontMetrics(this.storyArea.getFont());
        Insets insets = this.storyArea.getInsets();
        int n = Math.max(1, this.getStoryAreaHeight() - insets.top - insets.bottom - 16);
        return Math.max(1, n / Math.max(1, fontMetrics.getHeight()) - 1);
    }

    private int getStoryTextWidth() {
        Insets insets = this.storyArea.getInsets();
        int n = this.scenePanel.getWidth();
        if (n <= 0) {
            n = this.getContentPane().getWidth();
        }
        if (n <= 0) {
            n = this.getWidth();
        }
        int n2 = Math.max(1, n - 36);
        return Math.max(1, n2 - insets.left - insets.right);
    }

    private int countWrappedLines(String string) {
        if (string == null || string.isBlank()) {
            return 1;
        }
        int n = this.getStoryTextWidth();
        FontRenderContext fontRenderContext = new FontRenderContext(null, true, true);
        int n2 = 0;
        for (String string2 : string.split("\\n", -1)) {
            if (string2.isEmpty()) {
                ++n2;
                continue;
            }
            AttributedString attributedString = new AttributedString(string2);
            attributedString.addAttribute(TextAttribute.FONT, this.storyArea.getFont());
            LineBreakMeasurer lineBreakMeasurer = new LineBreakMeasurer(attributedString.getIterator(), fontRenderContext);
            while (lineBreakMeasurer.getPosition() < string2.length()) {
                lineBreakMeasurer.nextLayout(n);
                ++n2;
            }
        }
        return Math.max(1, n2);
    }

    private List<String> splitOversizeText(String string, int n) {
        String string2;
        ArrayList<String> arrayList = new ArrayList<String>();
        String string3 = string2 = string == null ? DEFAULT_PLAYER_NAME : string.strip();
        while (!string2.isEmpty()) {
            String string4;
            int n2;
            int n3 = 1;
            int n4 = string2.length();
            int n5 = 1;
            while (n3 <= n4) {
                n2 = (n3 + n4) / 2;
                string4 = string2.substring(0, n2).strip();
                if (string4.isEmpty()) {
                    n3 = n2 + 1;
                    continue;
                }
                if (this.countWrappedLines(string4) <= n && string4.length() <= 144) {
                    n5 = n2;
                    n3 = n2 + 1;
                    continue;
                }
                n4 = n2 - 1;
            }
            n2 = this.findReadableSplitIndex(string2, n5);
            string4 = string2.substring(0, n2).strip();
            if (string4.isEmpty()) {
                string4 = string2.substring(0, Math.min(1, string2.length()));
                n2 = string4.length();
            }
            arrayList.add(string4);
            string2 = string2.substring(n2).strip();
        }
        return arrayList;
    }

    private int findReadableSplitIndex(String string, int n) {
        int n2;
        for (int i = n2 = Math.max(1, Math.min(n, string.length())); i > Math.max(1, n2 - 12); --i) {
            char c = string.charAt(i - 1);
            if (!Character.isWhitespace(c) && c != ',' && c != '.' && c != '!' && c != '?') continue;
            return i;
        }
        return n2;
    }

    private String applyPlayerName(String string) {
        String string2 = this.state.playerName == null || this.state.playerName.isBlank() ? DEFAULT_PLAYER_NAME : this.state.playerName.strip();
        return string.replace("\ud50c\ub808\uc774\uc5b4", string2);
    }

    // 현재 인덱스의 페이지를 화면 상태에 반영한다.
    // 배경/캐릭터 이미지와 텍스트, 이름표가 여기서 함께 바뀐다.
    private void showCurrentPage() {
        this.currentPage = this.scenePages.get(this.currentPageIndex);
        this.activeBackgroundImage = this.currentPage.backgroundImage();
        this.activeCharacterImage = this.currentPage.characterImage();
        this.refreshImages();
        this.updateSpeakerBadge();
        this.layoutSceneLayers();
        this.startDialogueAnimation(this.currentPage.text());
    }

    // 스토리 진행의 핵심 분기:
    // 1) 타이핑 중이면 즉시 전체 공개
    // 2) 다음 페이지가 있으면 이동
    // 3) 마지막 페이지면 선택지 또는 엔딩 처리
    private void advanceStory() {
        if (this.choiceOverlay.isVisible()) {
            return;
        }
        if (this.dialogueTimer != null && this.dialogueTimer.isRunning() && !this.pageFullyVisible) {
            this.revealCurrentPageImmediately();
            return;
        }
        if (this.currentPageIndex + 1 < this.scenePages.size()) {
            ++this.currentPageIndex;
            this.showCurrentPage();
            return;
        }
        this.handleSceneEnd();
    }

    private void revealCurrentPageImmediately() {
        if (this.dialogueTimer != null && this.dialogueTimer.isRunning()) {
            this.dialogueTimer.stop();
        }
        this.storyArea.setText(this.currentPage.text());
        this.pageFullyVisible = true;
    }

    private void handleSceneEnd() {
        if (this.isEndingScene()) {
            this.showRestartOverlay();
            return;
        }
        if (this.currentScene == null || this.currentScene.choices.isEmpty()) {
            return;
        }
        if (this.visibleChoices.size() == 1) {
            this.performChoice(this.visibleChoices.get(0));
            return;
        }
        this.showChoicesOverlay();
    }

    private void performChoice(Choice choice) {
        choice.effect.accept(this.state);
        if (choice.recordsSuspicion) {
            this.state.suspectHistory.add(choice.label);
        }
        this.persistState();
        this.showScene(choice.nextSceneId);
    }

    private void showChoicesOverlay() {
        boolean bl;
        this.choicesPanel.removeAll();
        int n = this.currentScene.choices.size();
        boolean bl2 = bl = n <= 1;
        if (!bl) {
            for (Choice choice : this.currentScene.choices) {
                if (this.applyPlayerName(choice.label).length() < 12) continue;
                bl = true;
                break;
            }
        }
        int n2 = bl ? 1 : 2;
        int n3 = (int)Math.ceil((double)n / (double)n2);
        this.choicesPanel.setLayout(new GridLayout(n3, n2, 12, 12));
        for (Choice choice : this.currentScene.choices) {
            this.choicesPanel.add(this.createChoiceButton(choice));
        }
        int n4 = n2 == 1 ? 280 : 180;
        int n5 = n2 == 1 ? n4 : n4 * 2 + 12;
        int n6 = n3 * 64 + (n3 - 1) * 12 + 32;
        this.choicesPanel.setPreferredSize(new Dimension(n5, n6));
        this.choicesPanel.revalidate();
        this.choicesPanel.repaint();
        this.choiceOverlay.setVisible(true);
    }

    private void showRestartOverlay() {
        this.choicesPanel.removeAll();
        this.choicesPanel.setLayout(new GridLayout(1, 1, 0, 0));
        JButton jButton = this.createMenuButton("\ub2e4\uc2dc \uc2dc\uc791", this::showStartScreen);
        jButton.setHorizontalAlignment(0);
        jButton.setPreferredSize(new Dimension(220, 58));
        this.choicesPanel.add(jButton);
        this.choicesPanel.setPreferredSize(new Dimension(220, 58));
        this.choicesPanel.revalidate();
        this.choicesPanel.repaint();
        this.choiceOverlay.setVisible(true);
    }

    // 이름표는 화자 이름과 캐릭터 이미지가 모두 있을 때만 보이게 한다.
    private void updateSpeakerBadge() {
        if (this.currentScene == null) {
            this.speakerBadge.setText(" ");
            this.speakerBadge.setVisible(false);
            this.speakerPanel.setVisible(false);
            return;
        }
        String string = this.currentPage.speaker();
        boolean bl = string != null && !string.isBlank();
        boolean bl2 = this.currentPage.characterImage() != null && !this.currentPage.characterImage().isBlank();
        boolean bl3 = bl && bl2;
        this.speakerBadge.setText(bl3 ? this.applyPlayerName(string.strip()) : " ");
        this.speakerBadge.setVisible(bl3);
        this.speakerPanel.setVisible(bl3);
    }

    private boolean isEndingScene() {
        return this.isEndingSceneId(this.currentSceneId);
    }

    private boolean isEndingSceneId(String string) {
        return string != null && string.startsWith("ending_");
    }

    private String asWrappedHtml(String string, int n) {
        String string2 = string.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "<br>");
        return "<html><div style='width:" + n + "em; text-align:center;'>" + string2 + "</div></html>";
    }

    public static void main(String[] stringArray) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }

    private static final class StoryTextPanel
    extends JComponent {
        private String text = "";

        private StoryTextPanel() {
        }

        void setText(String string) {
            this.text = string == null ? Main.DEFAULT_PLAYER_NAME : string;
            this.repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2D = (Graphics2D)graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics2D.setColor(this.getForeground());
            graphics2D.setFont(this.getFont());
            Insets insets = this.getInsets();
            int n = insets.left;
            int n2 = insets.top;
            int n3 = Math.max(1, this.getWidth() - insets.left - insets.right);
            FontMetrics fontMetrics = graphics2D.getFontMetrics();
            for (String string : this.text.split("\\n", -1)) {
                if (string.isEmpty()) {
                    n2 += fontMetrics.getHeight();
                    continue;
                }
                AttributedString attributedString = new AttributedString(string);
                attributedString.addAttribute(TextAttribute.FONT, this.getFont());
                LineBreakMeasurer lineBreakMeasurer = new LineBreakMeasurer(attributedString.getIterator(), graphics2D.getFontRenderContext());
                while (lineBreakMeasurer.getPosition() < string.length()) {
                    TextLayout textLayout = lineBreakMeasurer.nextLayout(n3);
                    n2 = (int)((float)n2 + textLayout.getAscent());
                    textLayout.draw(graphics2D, n, n2);
                    n2 = (int)((float)n2 + (textLayout.getDescent() + textLayout.getLeading()));
                }
            }
            graphics2D.dispose();
        }
    }

    // 플레이 중 계속 유지되는 상태 묶음이다.
    // 어떤 사건을 해결했는지, 단서를 얻었는지, 엔딩을 봤는지 모두 여기 저장된다.
    private static class GameState {
        boolean poolSolved;
        boolean musicSolved;
        boolean scienceSolved;
        int truthScore;
        int suspicionScore;
        String finalChoice = "";
        String playerName = "";
        final Set<String> seenEndings = new LinkedHashSet<String>();
        final Set<String> clues = new LinkedHashSet<String>();
        final Set<String> visitedScenes = new LinkedHashSet<String>();
        final List<String> suspectHistory = new ArrayList<String>();
        final Map<String, String> evidenceBoard = new LinkedHashMap<String, String>();

        private GameState() {
        }

        void unlockClue(String string) {
            if (this.clues.add(string)) {
                this.evidenceBoard.put(string, "\ud655\ubcf4");
            }
        }

        void resetForNewRun() {
            this.poolSolved = false;
            this.musicSolved = false;
            this.scienceSolved = false;
            this.truthScore = 0;
            this.suspicionScore = 0;
            this.finalChoice = Main.DEFAULT_PLAYER_NAME;
            this.clues.clear();
            this.visitedScenes.clear();
            this.suspectHistory.clear();
            this.evidenceBoard.clear();
        }

        void reset() {
            this.resetForNewRun();
            this.playerName = Main.DEFAULT_PLAYER_NAME;
            this.seenEndings.clear();
        }
    }

    private record PageEntry(String text, String speaker, String backgroundImage, String characterImage) {
        private static PageEntry empty() {
            return new PageEntry(Main.DEFAULT_PLAYER_NAME, Main.DEFAULT_PLAYER_NAME, Main.DEFAULT_PLAYER_NAME, Main.DEFAULT_PLAYER_NAME);
        }
    }

    private static class LengthFilter
    extends DocumentFilter {
        private final int maxLength;

        LengthFilter(int n) {
            this.maxLength = n;
        }

        @Override
        public void insertString(DocumentFilter.FilterBypass filterBypass, int n, String string, AttributeSet attributeSet) throws BadLocationException {
            if (string == null) {
                return;
            }
            this.replace(filterBypass, n, 0, string, attributeSet);
        }

        @Override
        public void replace(DocumentFilter.FilterBypass filterBypass, int n, int n2, String string, AttributeSet attributeSet) throws BadLocationException {
            String string2 = string == null ? Main.DEFAULT_PLAYER_NAME : string;
            int n3 = filterBypass.getDocument().getLength();
            int n4 = n3 - n2 + string2.length();
            if (n4 <= this.maxLength) {
                super.replace(filterBypass, n, n2, string2, attributeSet);
                return;
            }
            int n5 = this.maxLength - (n3 - n2);
            if (n5 > 0) {
                super.replace(filterBypass, n, n2, string2.substring(0, n5), attributeSet);
            }
        }
    }

    private record SceneDefinition(String id, String title, String chapter, String narration, String speaker, String dialogue, String backgroundImage, String characterImage, String onEnter, List<PageDefinition> pages, List<ChoiceDefinition> choices) {
    }

    private static class Scene {
        final String title;
        final String chapter;
        final String narration;
        final String speaker;
        final String dialogue;
        final String backgroundImage;
        final String characterImage;
        final List<PageSpec> pages;
        final List<Choice> choices;
        final String bgm;
        final String sfx;
        final String transition;
        final Consumer<GameState> onEnter;

        Scene(String string, String string2, String string3, String string4, String string5, String string6, String string7, List<PageSpec> list, List<Choice> list2, Consumer<GameState> consumer) {
            this(string, string2, string3, string4, string5, string6, string7, list, list2, "ambient_night", Main.DEFAULT_PLAYER_NAME, "fade", consumer);
        }

        Scene(String string, String string2, String string3, String string4, String string5, String string6, String string7, List<PageSpec> list, List<Choice> list2, String string8, String string9, String string10, Consumer<GameState> consumer) {
            this.title = string;
            this.chapter = string2;
            this.narration = string3;
            this.speaker = string4;
            this.dialogue = string5;
            this.backgroundImage = string6;
            this.characterImage = string7;
            this.pages = list;
            this.choices = list2;
            this.bgm = string8;
            this.sfx = string9;
            this.transition = string10;
            this.onEnter = consumer;
        }
    }

    // 외부 라이브러리 없이 scenes.json을 읽기 위한 최소 JSON 파서다.
    // 현재 프로젝트에 필요한 object/array/string/boolean/null 범위만 직접 처리한다.
    private static final class SimpleJsonParser {
        private final String source;
        private int index;

        private SimpleJsonParser(String string) {
            this.source = string;
        }

        private Object parse() {
            this.skipWhitespace();
            Object object = this.parseValue();
            this.skipWhitespace();
            if (this.index != this.source.length()) {
                throw this.error("JSON \ub05d\uc5d0 \ubd88\ud544\uc694\ud55c \ubb38\uc790\uac00 \ub0a8\uc544 \uc788\uc2b5\ub2c8\ub2e4.");
            }
            return object;
        }

        private Object parseValue() {
            this.skipWhitespace();
            if (this.index >= this.source.length()) {
                throw this.error("\uac12\uc774 \ud544\uc694\ud55c \uc704\uce58\uc5d0\uc11c JSON\uc774 \ub05d\ub0ac\uc2b5\ub2c8\ub2e4.");
            }
            char c = this.source.charAt(this.index);
            return switch (c) {
                case '{' -> this.parseObject();
                case '[' -> this.parseArray();
                case '\"' -> this.parseString();
                case 't' -> this.parseLiteral("true", Boolean.TRUE);
                case 'f' -> this.parseLiteral("false", Boolean.FALSE);
                case 'n' -> this.parseLiteral("null", null);
                default -> throw this.error("\uc9c0\uc6d0\ud558\uc9c0 \uc54a\ub294 JSON \uac12 \uc2dc\uc791 \ubb38\uc790: " + c);
            };
        }

        private Map<String, Object> parseObject() {
            LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
            this.expect('{');
            this.skipWhitespace();
            if (this.peek('}')) {
                ++this.index;
                return linkedHashMap;
            }
            while (true) {
                String string = this.parseString();
                this.skipWhitespace();
                this.expect(':');
                linkedHashMap.put(string, this.parseValue());
                this.skipWhitespace();
                if (this.peek('}')) {
                    ++this.index;
                    return linkedHashMap;
                }
                this.expect(',');
            }
        }

        private List<Object> parseArray() {
            ArrayList<Object> arrayList = new ArrayList<Object>();
            this.expect('[');
            this.skipWhitespace();
            if (this.peek(']')) {
                ++this.index;
                return arrayList;
            }
            while (true) {
                arrayList.add(this.parseValue());
                this.skipWhitespace();
                if (this.peek(']')) {
                    ++this.index;
                    return arrayList;
                }
                this.expect(',');
            }
        }

        private String parseString() {
            this.expect('\"');
            StringBuilder stringBuilder = new StringBuilder();
            block9: while (this.index < this.source.length()) {
                char c;
                if ((c = this.source.charAt(this.index++)) == '\"') {
                    return stringBuilder.toString();
                }
                if (c == '\\') {
                    if (this.index >= this.source.length()) {
                        throw this.error("\ubb38\uc790\uc5f4 escape\uac00 \uc911\uac04\uc5d0 \ub05d\ub0ac\uc2b5\ub2c8\ub2e4.");
                    }
                    char c2 = this.source.charAt(this.index++);
                    switch (c2) {
                        case '\"': 
                        case '/': 
                        case '\\': {
                            stringBuilder.append(c2);
                            continue block9;
                        }
                        case 'b': {
                            stringBuilder.append('\b');
                            continue block9;
                        }
                        case 'f': {
                            stringBuilder.append('\f');
                            continue block9;
                        }
                        case 'n': {
                            stringBuilder.append('\n');
                            continue block9;
                        }
                        case 'r': {
                            stringBuilder.append('\r');
                            continue block9;
                        }
                        case 't': {
                            stringBuilder.append('\t');
                            continue block9;
                        }
                        case 'u': {
                            stringBuilder.append(this.parseUnicodeEscape());
                            continue block9;
                        }
                    }
                    throw this.error("\uc9c0\uc6d0\ud558\uc9c0 \uc54a\ub294 escape \ubb38\uc790: " + c2);
                }
                stringBuilder.append(c);
            }
            throw this.error("\ubb38\uc790\uc5f4\uc774 \ub2eb\ud788\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4.");
        }

        private char parseUnicodeEscape() {
            if (this.index + 4 > this.source.length()) {
                throw this.error("\uc720\ub2c8\ucf54\ub4dc escape \uae38\uc774\uac00 \ubd80\uc871\ud569\ub2c8\ub2e4.");
            }
            String string = this.source.substring(this.index, this.index + 4);
            this.index += 4;
            try {
                return (char)Integer.parseInt(string, 16);
            }
            catch (NumberFormatException numberFormatException) {
                throw this.error("\uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uc720\ub2c8\ucf54\ub4dc escape: " + string);
            }
        }

        private Object parseLiteral(String string, Object object) {
            if (!this.source.startsWith(string, this.index)) {
                throw this.error("\uc608\uc0c1\ud55c \ub9ac\ud130\ub7f4\uc774 \uc544\ub2d9\ub2c8\ub2e4: " + string);
            }
            this.index += string.length();
            return object;
        }

        private void skipWhitespace() {
            while (this.index < this.source.length() && Character.isWhitespace(this.source.charAt(this.index))) {
                ++this.index;
            }
        }

        private void expect(char c) {
            this.skipWhitespace();
            if (this.index >= this.source.length() || this.source.charAt(this.index) != c) {
                throw this.error("\uc608\uc0c1\ud55c \ubb38\uc790 '" + c + "'\ub97c \ucc3e\uc9c0 \ubabb\ud588\uc2b5\ub2c8\ub2e4.");
            }
            ++this.index;
        }

        private boolean peek(char c) {
            return this.index < this.source.length() && this.source.charAt(this.index) == c;
        }

        private IllegalArgumentException error(String string) {
            return new IllegalArgumentException(string + " (index=" + this.index + ")");
        }
    }

    private record ChoiceDefinition(String label, String nextSceneId, String effect, String visibility, boolean recordsSuspicion) {
    }

    private record PageDefinition(String text, String speaker, String backgroundImage, String characterImage) {
    }

    private static class Choice {
        final String label;
        final String nextSceneId;
        final Consumer<GameState> effect;
        final Predicate<GameState> visibility;
        final boolean recordsSuspicion;

        Choice(String string, String string2) {
            this(string, string2, gameState -> {}, gameState -> true, false);
        }

        Choice(String string, String string2, Consumer<GameState> consumer) {
            this(string, string2, consumer, gameState -> true, false);
        }

        Choice(String string, String string2, Consumer<GameState> consumer, Predicate<GameState> predicate) {
            this(string, string2, consumer, predicate, false);
        }

        Choice(String string, String string2, Consumer<GameState> consumer, Predicate<GameState> predicate, boolean bl) {
            this.label = string;
            this.nextSceneId = string2;
            this.effect = consumer;
            this.visibility = predicate;
            this.recordsSuspicion = bl;
        }

        boolean isVisible(GameState gameState) {
            return this.visibility.test(gameState);
        }
    }

    private record PageSpec(String text, String speaker, String backgroundImage, String characterImage) {
    }

    private static class SaveData {
        String playerName = "";
        String currentSceneId = "prologue_arrival";
        int textSpeedMs = 18;
        boolean poolSolved;
        boolean musicSolved;
        boolean scienceSolved;
        int truthScore;
        int suspicionScore;
        String finalChoice = "";
        final Set<String> seenEndings = new LinkedHashSet<String>();
        final Set<String> clues = new LinkedHashSet<String>();
        final Set<String> visitedScenes = new LinkedHashSet<String>();
        final List<String> suspectHistory = new ArrayList<String>();
        final Map<String, String> evidenceBoard = new LinkedHashMap<String, String>();

        private SaveData() {
        }

        static SaveData from(GameState gameState, String string, int n) {
            SaveData saveData = new SaveData();
            saveData.playerName = gameState.playerName;
            saveData.currentSceneId = string;
            saveData.textSpeedMs = n;
            saveData.poolSolved = gameState.poolSolved;
            saveData.musicSolved = gameState.musicSolved;
            saveData.scienceSolved = gameState.scienceSolved;
            saveData.truthScore = gameState.truthScore;
            saveData.suspicionScore = gameState.suspicionScore;
            saveData.finalChoice = gameState.finalChoice;
            saveData.seenEndings.addAll(gameState.seenEndings);
            saveData.clues.addAll(gameState.clues);
            saveData.visitedScenes.addAll(gameState.visitedScenes);
            saveData.suspectHistory.addAll(gameState.suspectHistory);
            saveData.evidenceBoard.putAll(gameState.evidenceBoard);
            return saveData;
        }

        void applyTo(GameState gameState) {
            gameState.resetForNewRun();
            gameState.playerName = this.playerName == null ? Main.DEFAULT_PLAYER_NAME : this.playerName;
            gameState.poolSolved = this.poolSolved;
            gameState.musicSolved = this.musicSolved;
            gameState.scienceSolved = this.scienceSolved;
            gameState.truthScore = this.truthScore;
            gameState.suspicionScore = this.suspicionScore;
            gameState.finalChoice = this.finalChoice == null ? Main.DEFAULT_PLAYER_NAME : this.finalChoice;
            gameState.seenEndings.addAll(this.seenEndings);
            gameState.clues.addAll(this.clues);
            gameState.visitedScenes.addAll(this.visitedScenes);
            gameState.suspectHistory.addAll(this.suspectHistory);
            gameState.evidenceBoard.putAll(this.evidenceBoard);
        }

        void save(Path path) {
            try {
                Files.createDirectories(path.getParent(), new FileAttribute[0]);
                Properties properties = new Properties();
                properties.setProperty("playerName", this.playerName == null ? Main.DEFAULT_PLAYER_NAME : this.playerName);
                properties.setProperty("currentSceneId", this.currentSceneId == null ? "prologue_arrival" : this.currentSceneId);
                properties.setProperty("textSpeedMs", Integer.toString(this.textSpeedMs));
                properties.setProperty("poolSolved", Boolean.toString(this.poolSolved));
                properties.setProperty("musicSolved", Boolean.toString(this.musicSolved));
                properties.setProperty("scienceSolved", Boolean.toString(this.scienceSolved));
                properties.setProperty("truthScore", Integer.toString(this.truthScore));
                properties.setProperty("suspicionScore", Integer.toString(this.suspicionScore));
                properties.setProperty("finalChoice", this.finalChoice == null ? Main.DEFAULT_PLAYER_NAME : this.finalChoice);
                properties.setProperty("seenEndings", String.join((CharSequence)"|", this.seenEndings));
                properties.setProperty("clues", String.join((CharSequence)"|", this.clues));
                properties.setProperty("visitedScenes", String.join((CharSequence)"|", this.visitedScenes));
                properties.setProperty("suspectHistory", String.join((CharSequence)"|", this.suspectHistory));
                for (Map.Entry<String, String> entry : this.evidenceBoard.entrySet()) {
                    properties.setProperty("evidence." + entry.getKey(), entry.getValue());
                }
                try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path, new OpenOption[0]);){
                    properties.store(bufferedWriter, "choice save data");
                }
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }

        static SaveData load(Path path) {
            Object object;
            if (!Files.exists(path, new LinkOption[0])) {
                return null;
            }
            Properties properties = new Properties();
            try {
                object = Files.newBufferedReader(path);
                try {
                    properties.load((Reader)object);
                }
                finally {
                    if (object != null) {
                        ((BufferedReader)object).close();
                    }
                }
            }
            catch (IOException iOException) {
                return null;
            }
            object = new SaveData();
            ((SaveData)object).playerName = properties.getProperty("playerName", Main.DEFAULT_PLAYER_NAME);
            ((SaveData)object).currentSceneId = properties.getProperty("currentSceneId", "prologue_arrival");
            ((SaveData)object).textSpeedMs = Integer.parseInt(properties.getProperty("textSpeedMs", "18"));
            ((SaveData)object).poolSolved = Boolean.parseBoolean(properties.getProperty("poolSolved", "false"));
            ((SaveData)object).musicSolved = Boolean.parseBoolean(properties.getProperty("musicSolved", "false"));
            ((SaveData)object).scienceSolved = Boolean.parseBoolean(properties.getProperty("scienceSolved", "false"));
            ((SaveData)object).truthScore = Integer.parseInt(properties.getProperty("truthScore", "0"));
            ((SaveData)object).suspicionScore = Integer.parseInt(properties.getProperty("suspicionScore", "0"));
            ((SaveData)object).finalChoice = properties.getProperty("finalChoice", Main.DEFAULT_PLAYER_NAME);
            SaveData.addAll(properties.getProperty("seenEndings", Main.DEFAULT_PLAYER_NAME), ((SaveData)object).seenEndings);
            SaveData.addAll(properties.getProperty("clues", Main.DEFAULT_PLAYER_NAME), ((SaveData)object).clues);
            SaveData.addAll(properties.getProperty("visitedScenes", Main.DEFAULT_PLAYER_NAME), ((SaveData)object).visitedScenes);
            SaveData.addAll(properties.getProperty("suspectHistory", Main.DEFAULT_PLAYER_NAME), ((SaveData)object).suspectHistory);
            for (String string : properties.stringPropertyNames()) {
                if (!string.startsWith("evidence.")) continue;
                ((SaveData)object).evidenceBoard.put(string.substring("evidence.".length()), properties.getProperty(string, Main.DEFAULT_PLAYER_NAME));
            }
            return (SaveData)object;
        }

        private static void addAll(String string, Set<String> set) {
            if (string == null || string.isBlank()) {
                return;
            }
            for (String string2 : string.split("\\|")) {
                if (string2.isBlank()) continue;
                set.add(string2);
            }
        }

        private static void addAll(String string, List<String> list) {
            if (string == null || string.isBlank()) {
                return;
            }
            for (String string2 : string.split("\\|")) {
                if (string2.isBlank()) continue;
                list.add(string2);
            }
        }
    }
}
