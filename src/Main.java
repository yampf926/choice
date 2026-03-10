import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Main extends JFrame {
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^.!?。！？\\n]+[.!?。！？]?|\\n+");
    private static final String DEFAULT_PLAYER_NAME = "";
    private static final int MAX_PAGE_CHARS = 72;
    private static final Path SAVE_PATH = Path.of("out", "save.properties");
    private static final double MOBILE_ASPECT_RATIO = 9.0 / 16.0;
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

    private final JLabel titleLabel = new JLabel("", SwingConstants.LEFT);
    private final JLabel chapterLabel = new JLabel("", SwingConstants.RIGHT);
    private final JLabel backgroundLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel characterLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel scenePanel = new JPanel(null);
    private final JPanel overlayPanel = new JPanel(new BorderLayout());
    private final JPanel hudPanel = new JPanel(new BorderLayout());
    private final JTextArea storyArea = new JTextArea();
    private final JLabel continueLabel = new JLabel("CLICK", SwingConstants.RIGHT);
    private final JPanel storyPanel = createStoryPanel();
    private final JLabel speakerBadge = new JLabel(" ", SwingConstants.LEFT);
    private final JPanel choiceOverlay = new JPanel(new GridBagLayout());
    private final JPanel choicesPanel = new JPanel(new GridLayout(0, 1, 0, 10));
    private final JPanel startOverlay = new JPanel(new GridBagLayout());
    private final JPanel galleryOverlay = new JPanel(new GridBagLayout());
    private final JTextField nameField = new JTextField("");
    private final JPanel galleryListPanel = new JPanel();
    private final Map<String, BufferedImage> imageCache = new LinkedHashMap<>();
    private JButton continueGameButton;

    private final Map<String, Scene> scenes = new LinkedHashMap<>();
    private final GameState state = new GameState();
    private final Map<String, String> endingTitles = new LinkedHashMap<>();
    private final Map<String, String> endingDescriptions = new LinkedHashMap<>();

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

    public Main() {
        setTitle("월야고등학교: 침묵의 기록");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(450, 800);
        setMinimumSize(new Dimension(360, 640));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(NIGHT);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        JPanel topBar = createTopBar();
        titleLabel.setForeground(new Color(220, 225, 233));
        titleLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 24));
        titleLabel.setBorder(new EmptyBorder(6, 8, 10, 8));
        chapterLabel.setForeground(new Color(160, 171, 192));
        chapterLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        chapterLabel.setBorder(new EmptyBorder(10, 8, 10, 8));
        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(chapterLabel, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        scenePanel.setOpaque(false);
        root.add(scenePanel, BorderLayout.CENTER);

        backgroundLabel.setOpaque(true);
        backgroundLabel.setBackground(new Color(12, 16, 24));
        backgroundLabel.setForeground(new Color(170, 178, 194));
        backgroundLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 18));
        backgroundLabel.setHorizontalAlignment(SwingConstants.CENTER);
        backgroundLabel.setVerticalAlignment(SwingConstants.CENTER);
        scenePanel.add(backgroundLabel);

        characterLabel.setOpaque(false);
        characterLabel.setHorizontalAlignment(SwingConstants.CENTER);
        characterLabel.setVerticalAlignment(SwingConstants.BOTTOM);
        characterLabel.setForeground(new Color(240, 244, 250));
        characterLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        scenePanel.add(characterLabel);

        overlayPanel.setOpaque(false);
        scenePanel.add(overlayPanel);

        storyArea.setEditable(false);
        storyArea.setLineWrap(true);
        storyArea.setWrapStyleWord(true);
        storyArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 18));
        storyArea.setForeground(new Color(229, 233, 239));
        storyArea.setBackground(PANEL);
        storyArea.setOpaque(true);
        storyArea.setBorder(new EmptyBorder(16, 18, 8, 18));
        storyArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                advanceStory();
            }
        });

        continueLabel.setForeground(ACCENT_SOFT);
        continueLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
        continueLabel.setBorder(new EmptyBorder(0, 0, 4, 14));

        speakerBadge.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        speakerBadge.setForeground(new Color(238, 241, 246));
        speakerBadge.setOpaque(true);
        speakerBadge.setBackground(new Color(88, 96, 116));
        speakerBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ACCENT_SOFT, 1),
                        BorderFactory.createLineBorder(new Color(255, 255, 255, 20), 1)
                ),
                new EmptyBorder(7, 12, 7, 12)
        ));

        storyPanel.setOpaque(false);
        storyPanel.setPreferredSize(new Dimension(0, 180));
        storyPanel.setMinimumSize(new Dimension(0, 180));
        storyPanel.add(storyArea, BorderLayout.CENTER);
        storyPanel.add(speakerBadge, BorderLayout.NORTH);
        storyPanel.add(continueLabel, BorderLayout.SOUTH);
        storyPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                advanceStory();
            }
        });

        hudPanel.setOpaque(false);
        hudPanel.setBorder(new EmptyBorder(0, 18, 14, 18));
        hudPanel.add(storyPanel, BorderLayout.SOUTH);

        choiceOverlay.setOpaque(false);
        choicesPanel.setOpaque(true);
        choicesPanel.setBackground(PANEL);
        choicesPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(PANEL_EDGE, 1),
                        BorderFactory.createLineBorder(new Color(255, 255, 255, 28), 1)
                ),
                new EmptyBorder(16, 16, 16, 16)
        ));
        choiceOverlay.add(choicesPanel);
        choiceOverlay.setVisible(false);

        startOverlay.setOpaque(false);
        startOverlay.add(createStartPanel());

        galleryOverlay.setOpaque(false);
        galleryOverlay.add(createGalleryPanel());
        galleryOverlay.setVisible(false);

        overlayPanel.add(createBottomShade(), BorderLayout.CENTER);
        overlayPanel.add(choiceOverlay, BorderLayout.CENTER);
        overlayPanel.add(hudPanel, BorderLayout.SOUTH);
        scenePanel.add(startOverlay);
        scenePanel.add(galleryOverlay);

        initEndingMetadata();
        initScenes();
        resetState();
        loadSaveData();
        installKeyBindings();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                enforceMobileAspectRatio();
                layoutSceneLayers();
                refreshImages();
            }
        });

        showStartScreen();
        SwingUtilities.invokeLater(() -> {
            layoutSceneLayers();
            refreshImages();
            scenePanel.repaint();
        });
    }

    private JComponent createBottomShade() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                GradientPaint paint = new GradientPaint(0, 0, new Color(5, 7, 10, 0), 0, getHeight(), new Color(8, 10, 16, 210));
                g2.setPaint(paint);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setPaint(new GradientPaint(0, getHeight() / 3f, new Color(0, 0, 0, 0), 0, getHeight(), ACCENT_GLOW));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(52, 58, 69, 236));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(new Color(198, 206, 220, 70));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    private JPanel createStoryPanel() {
        return new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 46));
                g2.fillRoundRect(6, 8, getWidth() - 12, getHeight() - 6, 24, 24);
                g2.setColor(new Color(52, 58, 69, 236));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(new Color(198, 206, 220, 70));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
                g2.setColor(new Color(116, 125, 144, 150));
                g2.drawLine(20, getHeight() - 18, getWidth() - 20, getHeight() - 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    private JPanel createStartPanel() {
        JPanel panel = createOverlayCard(new GridBagLayout());
        panel.setBorder(new EmptyBorder(22, 22, 22, 22));
        panel.setPreferredSize(new Dimension(320, 424));
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setPreferredSize(new Dimension(240, 286));

        JLabel title = new JLabel("월야고등학교");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 24));
        title.setForeground(new Color(212, 218, 228));

        JLabel prompt = new JLabel("이름");
        prompt.setAlignmentX(Component.CENTER_ALIGNMENT);
        prompt.setHorizontalAlignment(SwingConstants.CENTER);
        prompt.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        prompt.setForeground(ACCENT);
        prompt.setBorder(new EmptyBorder(12, 0, 6, 0));

        nameField.setMaximumSize(new Dimension(240, 36));
        nameField.setPreferredSize(new Dimension(240, 36));
        nameField.setFont(new Font("Malgun Gothic", Font.PLAIN, 16));
        nameField.setHorizontalAlignment(JTextField.CENTER);
        nameField.setBackground(new Color(73, 80, 93));
        nameField.setForeground(new Color(232, 236, 242));
        nameField.setCaretColor(ACCENT);
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(110, 120, 138), 1),
                new EmptyBorder(8, 10, 8, 10)
        ));
        ((AbstractDocument) nameField.getDocument()).setDocumentFilter(new LengthFilter(5));
        nameField.addActionListener(e -> startGame());

        JButton startButton = createMenuButton("시작", this::startGame);
        continueGameButton = createMenuButton("이어하기", this::continueGame);
        JButton endingButton = createMenuButton("엔딩 모음", this::showEndingGallery);
        JButton clearButton = createMenuButton("기록 삭제", this::clearSaveData);
        continueGameButton.setEnabled(Files.exists(SAVE_PATH));

        content.add(title);
        content.add(prompt);
        content.add(nameField);
        content.add(Box.createVerticalStrut(14));
        content.add(startButton);
        content.add(Box.createVerticalStrut(8));
        content.add(continueGameButton);
        content.add(Box.createVerticalStrut(8));
        content.add(endingButton);
        content.add(Box.createVerticalStrut(8));
        content.add(clearButton);
        panel.add(content);
        return panel;
    }

    private JPanel createGalleryPanel() {
        JPanel panel = createOverlayCard(new BorderLayout(0, 14));
        panel.setBorder(new EmptyBorder(22, 22, 22, 22));
        panel.setPreferredSize(new Dimension(320, 460));

        JLabel title = new JLabel("엔딩 모음");
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 21));
        title.setForeground(new Color(216, 221, 230));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(title, BorderLayout.NORTH);

        galleryListPanel.setLayout(new BoxLayout(galleryListPanel, BoxLayout.Y_AXIS));
        galleryListPanel.setOpaque(false);
        panel.add(galleryListPanel, BorderLayout.CENTER);

        JButton backButton = createMenuButton("돌아가기", this::showStartScreen);
        panel.add(backButton, BorderLayout.SOUTH);
        return panel;
    }

    private JButton createMenuButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setFocusPainted(false);
        button.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
        button.setBackground(BUTTON);
        button.setForeground(new Color(233, 236, 242));
        button.setBorder(choiceBorder(new Color(108, 118, 136), new Color(255, 255, 255, 24)));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setMaximumSize(new Dimension(240, 42));
        button.setPreferredSize(new Dimension(240, 42));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(BUTTON_HOVER);
                button.setBorder(choiceBorder(ACCENT, new Color(255, 255, 255, 32)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(BUTTON);
                button.setBorder(choiceBorder(new Color(108, 118, 136), new Color(255, 255, 255, 24)));
            }
        });
        button.addActionListener(e -> action.run());
        return button;
    }

    private JPanel createOverlayCard(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 54));
                g2.fillRoundRect(6, 8, getWidth() - 12, getHeight() - 8, 24, 24);
                g2.setPaint(new GradientPaint(0, 0, new Color(44, 49, 60, 242), 0, getHeight(), new Color(55, 61, 73, 238)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 26, 26);
                g2.setColor(new Color(192, 199, 211, 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 26, 26);
                g2.setColor(new Color(108, 118, 136, 130));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 24, 24);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    private void initEndingMetadata() {
        endingTitles.put("ending_true", "진실 루트");
        endingTitles.put("ending_wrong_accusation", "오판 루트");
        endingTitles.put("ending_silence", "침묵 루트");

        endingDescriptions.put("ending_true", "무죄를 증명하고 사건을 사고로 바로잡은 결말");
        endingDescriptions.put("ending_wrong_accusation", "누군가를 범인으로 단정해 비극을 남긴 결말");
        endingDescriptions.put("ending_silence", "결론을 보류한 채 기록만 남긴 결말");
    }

    private void installKeyBindings() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "advance-story");
        actionMap.put("advance-story", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (startOverlay.isVisible() || galleryOverlay.isVisible()) {
                    return;
                }
                advanceStory();
            }
        });
    }

    private void initScenes() {
        scenes.put("prologue_arrival", new Scene(
                "월야고등학교 정문",
                "Chapter 1  프롤로그",
                """
                산속 깊은 곳의 사립 기숙학교, 월야고등학교.
                학생 사망 사고가 몇 년 사이 연달아 발생했지만 학교는 모두 사고로 발표했다.
                밤이 되면 학교는 외부와 단절되고, 학생들은 네 명의 귀신이 학교를 떠돌고 있다고 수군댄다.
                """,
                "교장",
                "월야고등학교에 와주셔서 감사합니다. 학교를 뒤흔드는 소문을 조용히 정리해 주십시오.",
                "bg_school_gate_night.png",
                "ch_principal.png",
                List.of(new Choice("교장실로 향한다", "prologue_principal")),
                null
        ));

        scenes.put("prologue_principal", new Scene(
                "교장실 보고",
                "Chapter 1  프롤로그",
                """
                교장은 사건이 외부에 알려지는 것을 무엇보다 두려워한다.
                공식 발표는 모두 사고였지만, 학생들 사이에서는 '살인 사건이 귀신이 되어 돌아왔다'는 공포가 퍼지고 있다.
                플레이어는 학교의 평판이 아니라 진실을 확인하기 위해 조사를 시작한다.
                """,
                "플레이어",
                "공식 기록이 사고라면, 왜 학생들은 살인이라고 믿게 되었을까.",
                "bg_archive_room.png",
                "ch_exorcist.png",
                List.of(new Choice("보건교사 양지영을 만난다", "prologue_nurse")),
                null
        ));

        scenes.put("prologue_nurse", new Scene(
                "보건실",
                "Chapter 1  프롤로그",
                """
                보건교사 양지영은 네 사건의 피해자들이 모두 억울함을 남긴 채 죽었다고 말한다.
                특히 마지막 피해자인 주다영은 앞선 사건들을 조사하며 김현진이 범인이라고 확신하고 있었다.
                양지영은 조사에 협조하겠다고 하지만, 어딘가 지친 표정을 숨기지 못한다.
                """,
                "양지영",
                "세 건... 아니요, 마지막 한 건까지 합치면 네 건이에요. 다들 사고라고 했지만 아무도 그렇게 믿지 않았어요.",
                "bg_main_hall_night.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("밤 복도를 조사한다", "prologue_hall")),
                null
        ));

        scenes.put("prologue_hall", new Scene(
                "밤 복도",
                "Chapter 1  프롤로그",
                """
                복도 전등이 몇 번 깜빡인다.
                잔류 기억이 스치듯 지나가고, 플레이어의 시야 끝에 수영장 쪽으로 젖은 발자국이 이어진다.
                학교의 첫 공포는 명백한 위협보다, 누군가가 남긴 단편적인 오해에서 시작되고 있었다.
                """,
                "플레이어",
                "환영은 진실 그 자체가 아니다. 누군가의 마지막 감정일 뿐이다.",
                "bg_main_hall_night.png",
                "ch_exorcist.png",
                List.of(new Choice("사건 정리로 간다", "case_hub")),
                null
        ));

        scenes.put("pool_intro", new Scene(
                "수영장 사고",
                "Chapter 2  수영장 사건",
                """
                피해자: 염선혜. 영상 동아리 학생.
                학생들은 '수영장 CCTV 공백과 문 앞 그림자' 때문에 누군가 선혜를 물에 밀어 넣었다고 믿는다.
                하지만 현장은 살인과 사고 어느 쪽으로도 읽히는 단서들이 뒤섞여 있다.
                """,
                "양지영",
                "선혜는 영상 공모전을 준비하고 있었어요. 그런데 애가 죽은 밤, 영상에는 사람 그림자가 찍혔어요.",
                "bg_pool_night.png",
                "ch_yang_jiyeong.png",
                List.of(
                        new Choice("선혜의 카메라 영상을 본다", "pool_video")
                ),
                null
        ));

        scenes.put("pool_video", new Scene(
                "카메라 영상",
                "Chapter 2  수영장 사건",
                """
                영상 마지막 프레임에는 수영장 문 근처를 스쳐 지나가는 그림자가 남아 있다.
                물은 이상할 정도로 크게 흔들렸고, 바닥 타일에는 미끄러진 듯한 긁힘이 남아 있다.
                청소 장치가 자동 가동된 기록도 같은 시각에 남아 있다.
                """,
                "플레이어",
                "그림자만 보면 범인이 있는 것처럼 보이지만, 물의 흔들림은 사람보다 기계에 가깝다.",
                "bg_pool_edge.png",
                "ch_ghost_yeom_seonhye.png",
                List.of(
                        new Choice("학생 증언을 듣는다", "pool_interview"),
                        new Choice("추리한다", "pool_deduction")
                ),
                g -> g.unlockClue("수영장 영상")
        ));

        scenes.put("pool_interview", new Scene(
                "학생 인터뷰",
                "Chapter 2  수영장 사건",
                """
                학생들은 선혜가 혼자 촬영 장비를 들고 수영장으로 내려갔다고 증언한다.
                누군가 따라갔다는 말도 있지만, 정작 그 그림자는 청소 직원이 순찰하던 시간대와 겹친다.
                공백이 있던 CCTV는 장비 점검으로 자동 저장이 끊겼던 것으로 보인다.
                """,
                "플레이어",
                "단서는 다 모였다.",
                "bg_pool_night.png",
                "ch_exorcist.png",
                List.of(new Choice("추리한다", "pool_deduction")),
                g -> g.unlockClue("청소 장치 기록")
        ));

        scenes.put("pool_deduction", new Scene(
                "수영장 추리",
                "Chapter 2  수영장 사건",
                """
                단서 조합:
                그림자 / CCTV 공백 / 타일 긁힘 / 물살 기록
                어떤 결론이 가장 논리적인가.
                """,
                "플레이어",
                "패턴을 만들기는 쉽다. 하지만 지금 필요한 건 가장 극적인 설명이 아니라 가장 단순한 설명이다.",
                "bg_pool_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("누군가에 의한 타살로 본다", "pool_wrong", g -> g.suspicionScore++, g -> true, true),
                        new Choice("자동 청소 장치와 미끄러짐에 의한 사고로 본다", "pool_true", g -> {
                            g.poolSolved = true;
                            g.truthScore++;
                            g.unlockClue("염선혜 사건 해결");
                        })
                ),
                null
        ));

        scenes.put("pool_wrong", new Scene(
                "수영장 오판",
                "Chapter 2  수영장 사건",
                """
                그림자와 CCTV 공백은 살인을 떠올리게 한다.
                그러나 긁힌 타일의 방향과 물살 기록은 누군가 밀었다기보다 급하게 균형을 잃은 장면에 가깝다.
                플레이어는 타살 가설이 강렬하지만 불완전하다는 사실을 깨닫는다.
                """,
                "염선혜의 잔류 기억",
                "마치 물 속으로 빨려들어가는 듯 했어.",
                "bg_pool_edge.png",
                "ch_ghost_yeom_seonhye_soft.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("pool_true", new Scene(
                "수영장 사건 결론",
                "Chapter 2  수영장 사건",
                """
                염선혜는 밤 수중 촬영 도중 자동 청소 장치가 가동되며 크게 흔들린 물살에 균형을 잃었다.
                젖은 타일에서 미끄러져 머리를 부딪힌 뒤 익사했다.
                그림자는 청소 직원의 순찰이 남긴 우연한 흔적이었다.
                """,
                "양지영",
                "살인이 아니라 사고였다는 사실이 더 허무하게 느껴지죠. 그런데도 다들 더 무서운 이야기를 믿고 싶어 했어요.",
                "bg_pool_edge.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("music_intro", new Scene(
                "음악실 사고",
                "Chapter 3  음악실 사건",
                """
                피해자: 한승준. 피아노 콩쿠르 준비생.
                녹음 파일에는 발걸음 소리와 문 여닫는 소리, 그리고 피아노 뚜껑이 세게 닫히는 충격음이 남아 있다.
                학생들은 그 소리가 누군가의 침입과 공격이라고 믿는다.
                """,
                "플레이어",
                "소리는 언제나 상상력을 자극한다. 보이지 않는 인물을 만들어내기 가장 쉬운 단서다.",
                "bg_music_room_night.png",
                "ch_ghost_han_seungjun.png",
                List.of(
                        new Choice("녹음 파일을 분석한다", "music_record")
                ),
                null
        ));

        scenes.put("music_record", new Scene(
                "녹음 파일",
                "Chapter 3  음악실 사건",
                """
                파일에는 연주, 발걸음, 문 닫힘 소리, 급한 숨, 금속 스탠드가 긁히는 잡음이 순서대로 남아 있다.
                한승준의 친구는 그날 김현진이 복도를 지나갔다고 말한다.
                김현진은 음악실 안에는 들어가지 않았고, 열린 문을 닫았을 뿐이라고 주장한다.
                """,
                "한승준의 잔류 기억",
                "내 죽음의 이유를 누군가의 탓으로 돌리고 싶었어.",
                "bg_music_room_close.png",
                "ch_ghost_han_seungjun_soft.png",
                List.of(new Choice("추리한다", "music_deduction")),
                g -> g.unlockClue("음악실 녹음")
        ));

        scenes.put("music_deduction", new Scene(
                "음악실 추리",
                "Chapter 3  음악실 사건",
                """
                단서 조합:
                발걸음 소리 / 문 닫힘 / 마이크 감도 / 스탠드 전선 / 뚜껑 충격
                가장 일관된 설명은 무엇인가.
                """,
                "플레이어",
                "단서는 다 모였다.",
                "bg_music_room_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("김현진이 음악실에 들어와 해쳤다고 본다", "music_wrong", g -> g.suspicionScore++, g -> true, true),
                        new Choice("건반 위의 손에 피아노 뚜껑이 덮인 사고로 본다", "music_true", g -> {
                            g.musicSolved = true;
                            g.truthScore++;
                            g.unlockClue("한승준 사건 해결");
                        })
                ),
                null
        ));

        scenes.put("music_wrong", new Scene(
                "음악실 오판",
                "Chapter 3  음악실 사건",
                """
                발걸음과 문 닫힘 소리는 누군가 개입했다는 확신을 준다.
                그러나 마이크 감도는 복도 소리를 과장했고, 전선 위치는 승준이 먼저 균형을 잃었음을 가리킨다.
                김현진은 의심받을 만한 위치에 있었지만 범행을 저질렀다는 증거는 아니다.
                """,
                "플레이어",
                "사건 근처에 있었다는 사실과 범인이라는 결론 사이에는 생각보다 큰 간격이 있다.",
                "bg_music_room_night.png",
                "ch_exorcist.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("music_true", new Scene(
                "음악실 사건 결론",
                "Chapter 3  음악실 사건",
                """
                진실:
                한승준은 밤 연주를 녹음하던 중 마이크 스탠드 전선에 발이 걸려 넘어졌다.
                피아노를 짚는 과정에서 손이 안쪽으로 들어가 있었고, 닫히던 뚜껑에 손가락이 크게 손상되었다.
                녹음 파일 속 발걸음은 복도를 지나던 김현진의 소리였고, 문 닫힘은 열린 문을 정리한 흔적이었다.
                """,
                "양지영",
                "모든 단서가 누군가를 가리키는 것처럼 보여도, 그게 곧 죄를 뜻하는 건 아니에요.",
                "bg_music_room_close.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("science_intro", new Scene(
                "과학실 사고",
                "Chapter 4  과학실 사건",
                """
                피해자: 김준영. 과학 경진대회 준비생.
                CCTV에는 다른 학생이 잠깐 드나드는 장면이 남아 있고, 실험 기록은 약품이 바뀐 것처럼 보이게 적혀 있다.
                주다영의 노트에는 '공통점 - 김현진'이라는 메모가 반복된다.
                """,
                "플레이어",
                "세 번째 사건까지 오면 패턴은 거의 완성된 것처럼 보인다. 그래서 더 위험하다.",
                "bg_science_lab_night.png",
                "ch_ghost_kim_junyeong.png",
                List.of(
                        new Choice("실험 노트와 용기를 조사한다", "science_lab")
                ),
                null
        ));

        scenes.put("science_lab", new Scene(
                "실험 준비 흔적",
                "Chapter 4  과학실 사건",
                """
                용기 하나의 라벨이 흐릿하게 지워져 있다.
                김준영은 강산 대신 다른 시약을 쓰고 있다고 믿었고, 금속 도구와 만나 수소 가스가 발생했다.
                알코올 램프 불꽃이 닿자 폭발이 일어났다.
                """,
                "플레이어",
                "누군가 바꿔치기한 것처럼 보였지만, 실제로는 관리 부실과 실수가 겹친 비극일 수 있다.",
                "bg_science_explosion_mark.png",
                "ch_ghost_kim_junyeong_soft.png",
                List.of(new Choice("추리한다", "science_deduction")),
                g -> g.unlockClue("라벨 지워진 용기")
        ));

        scenes.put("science_deduction", new Scene(
                "과학실 추리",
                "Chapter 4  과학실 사건",
                """
                단서 조합:
                CCTV 학생 / 변경된 기록처럼 보이는 노트 / 라벨 지워진 용기 / 폭발 반응
                이 사건은 조작인가, 실수인가.
                """,
                "플레이어",
                "지금 김현진을 범인으로 결론내리면 모든 사건이 하나로 묶인다. 그래서 오히려 의심해야 한다.",
                "bg_science_lab_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("김현진이 약품을 바꿨다고 본다", "science_wrong", g -> g.suspicionScore++, g -> true, true),
                        new Choice("라벨이 지워진 용기를 착각한 실험 사고로 본다", "science_true", g -> {
                            g.scienceSolved = true;
                            g.truthScore++;
                            g.unlockClue("김준영 사건 해결");
                        })
                ),
                null
        ));

        scenes.put("science_wrong", new Scene(
                "과학실 오판",
                "Chapter 4  과학실 사건",
                """
                CCTV에 찍힌 다른 학생과 수정된 기록은 누군가 개입한 흔적처럼 보인다.
                그러나 실제 반응 순서는 관리 부실과 실험 실수만으로도 충분히 설명된다.
                공통점이 있다고 해서 공통 범인이 반드시 있는 것은 아니다.
                """,
                "김준영의 잔류 기억",
                "조금만 더 주의했더라면...",
                "bg_science_explosion_mark.png",
                "ch_ghost_kim_junyeong_soft.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("science_true", new Scene(
                "과학실 사건 결론",
                "Chapter 4  과학실 사건",
                """
                진실:
                김준영은 라벨이 지워진 용기를 잘못 집었다.
                강산과 금속의 반응으로 수소 가스가 생겼고, 알코올 램프 불꽃이 닿으며 폭발이 일어났다.
                CCTV 속 학생과 기록 수정은 사건을 더 미스터리하게 보이게 했을 뿐, 직접적 원인은 아니었다.
                """,
                "플레이어",
                "세 사건 모두 사고였다. 그런데 왜 모두가 김현진을 범인이라고 믿게 되었을까.",
                "bg_science_lab_night.png",
                "ch_exorcist.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("case_hub", new Scene(
                "사건 정리",
                "Chapter 1-4  사건 허브",
                """
                월야고에는 네 명의 귀신이 있다는 소문이 돈다.
                수영장, 음악실, 과학실, 그리고 옥상.
                플레이어는 수집한 단서를 다시 연결하며, 사건들이 정말 하나의 연쇄 살인인지 스스로 점검해야 한다.
                """,
                "플레이어",
                "모든 사건을 하나로 연결하고 싶은 유혹이 든다. 하지만 그 유혹 자체가 함정일 수도 있다.",
                "bg_main_hall_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("수영장 사건", "pool_intro", g -> {}, g -> !g.poolSolved),
                        new Choice("음악실 사건", "music_intro", g -> {}, g -> g.poolSolved && !g.musicSolved),
                        new Choice("과학실 사건", "science_intro", g -> {}, g -> g.poolSolved && g.musicSolved && !g.scienceSolved),
                        new Choice("옥상 사건", "rooftop_intro", g -> {}, g -> g.poolSolved && g.musicSolved && g.scienceSolved && !g.visitedScenes.contains("rooftop_intro"))
                ),
                null
        ));

        scenes.put("rooftop_intro", new Scene(
                "옥상 사건",
                "Chapter 5  옥상 사건",
                """
                마지막 피해자는 주다영.
                주다영은 앞선 사건들을 조사하며 김현진이 범인이라고 믿었고, 자신의 노트에 의심의 논리를 차곡차곡 쌓아 두었다.
                옥상에는 다툼의 흔적, 젖은 바닥, 난간 충돌 흔적, 그리고 다영이 남긴 메모가 남아 있다.
                """,
                "양지영",
                "다영은 누구보다 진실을 믿고 싶어 했어요. 그런데 그 진실이 너무 빨리 하나의 결론으로 굳어졌죠.",
                "bg_rooftop_night.png",
                "ch_yang_jiyeong_serious.png",
                List.of(
                        new Choice("다영의 노트를 읽는다", "rooftop_note"),
                        new Choice("최종 추리를 한다", "final_board")
                ),
                null
        ));

        scenes.put("rooftop_note", new Scene(
                "주다영의 노트",
                "Chapter 5  옥상 사건",
                """
                노트에는 반복해서 같은 문장이 적혀 있다.
                '공통점 - 김현진'
                '기록 수정'
                '시설 열쇠'
                '사건 현장 근처 목격'
                단서들은 누군가를 지목하는 데 충분해 보이지만, 동시에 전부 다른 설명도 가능하다.
                """,
                "주다영의 잔류 기억",
                "내가 틀리면 안 된다고 생각했어. 그래야 이 죽음들이 견딜 만한 이야기가 되니까.",
                "bg_rooftop_edge.png",
                "ch_ghost_ju_dayeong.png",
                List.of(new Choice("최종 추리를 한다", "final_board")),
                g -> g.unlockClue("주다영 노트")
        ));

        scenes.put("final_board", new Scene(
                "최종 추리",
                "Final Board",
                """
                세 건의 선행 사건은 모두 사고로 설명 가능하다.
                그러나 주다영은 그 사실을 확인하기 전에 김현진을 옥상으로 불러 추궁했다.
                언쟁 끝에 주다영은 젖은 바닥에서 미끄러져 난간에 부딪혔고, 그 사고가 마지막 비극이 되었다.
                이제 플레이어는 하나의 질문에 답해야 한다.
                '김현진은 범인인가?'
                """,
                "플레이어",
                "내가 찾는 것은 범인인가, 아니면 오해의 구조인가.",
                "bg_chapel_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("김현진이 범인이다", "ending_wrong_accusation", g -> g.finalChoice = "accuse"),
                        new Choice("김현진은 범인이 아니다", "ending_true", g -> g.finalChoice = "innocent"),
                        new Choice("확신할 수 없다", "ending_silence", g -> g.finalChoice = "silence")
                ),
                null
        ));

        scenes.put("ending_true", new Scene(
                "진실 루트",
                "Ending  True",
                """
                플레이어는 김현진의 무죄를 증명하고, 각 사건을 사고로 다시 기록한다.
                월야고를 떠돌던 잔류 기억은 더 이상 '범인'을 만들지 않는다.
                주다영의 마지막 환영은 자신이 틀렸음을 받아들이고, 네 명의 귀신은 조용히 사라진다.
                """,
                "주다영의 잔류 기억",
                "사람들은 우연을 견디지 못해서 이야기를 만들지. 그런데 그 이야기가 또 다른 비극을 만들었네.",
                "bg_school_dawn.png",
                "ch_ghost_group_fade.png",
                List.of(new Choice("시작 화면으로", "prologue_arrival", GameState::reset)),
                null
        ));

        scenes.put("ending_wrong_accusation", new Scene(
                "오판 루트",
                "Ending  Wrong Accusation",
                """
                플레이어는 김현진을 범인으로 지목한다.
                학교는 그 결론을 이용해 사건을 '살인'으로 봉합하고 평판을 지키려 한다.
                김현진은 퇴학당하지만, 각 사건의 진짜 원인은 끝내 바로잡히지 않는다.
                귀신들은 해방되지 못한 채 학교에 남는다.
                """,
                "플레이어",
                "모든 조각이 맞아떨어진다고 믿었다. 하지만 너무 완벽한 이야기는 진실이 아니라 욕망일 수도 있다.",
                "bg_rooftop_night.png",
                "",
                List.of(new Choice("시작 화면으로", "prologue_arrival", GameState::reset)),
                null
        ));

        scenes.put("ending_silence", new Scene(
                "침묵 루트",
                "Ending  Silence",
                """
                플레이어는 결론을 보류한다.
                학교는 사건들을 계속 사고로 남겨 둔 채 침묵을 선택하고, 학생들의 공포는 사라지지 않는다.
                진실에 닿을 기회는 있었지만, 아무도 그것을 끝까지 기록하지 못한다.
                """,
                "양지영",
                "아무 말도 하지 않는다고 해서 상처가 사라지진 않아요. 다만 누가 무엇을 믿는지만 더 흐려질 뿐이죠.",
                "bg_main_hall_night.png",
                "ch_yang_jiyeong_confess.png",
                List.of(new Choice("시작 화면으로", "prologue_arrival", GameState::reset)),
                null
        ));
    }

    private void resetState() {
        state.reset();
    }

    private void showStartScreen() {
        currentScene = null;
        currentSceneId = "start_menu";
        activeBackgroundImage = "bg_school_gate_night.png";
        activeCharacterImage = "";
        titleLabel.setText("월야고등학교");
        chapterLabel.setText("START");
        nameField.setText(state.playerName == null || state.playerName.isBlank() ? DEFAULT_PLAYER_NAME : state.playerName);
        storyArea.setText("");
        speakerBadge.setVisible(false);
        choiceOverlay.setVisible(false);
        galleryOverlay.setVisible(false);
        startOverlay.setVisible(true);
        hudPanel.setVisible(false);
        refreshContinueButton();
        layoutSceneLayers();
        refreshImages();
        SwingUtilities.invokeLater(() -> nameField.requestFocusInWindow());
    }

    private void startGame() {
        String enteredName = nameField.getText() == null ? "" : nameField.getText().trim();
        if (enteredName.isEmpty()) {
            nameField.requestFocusInWindow();
            return;
        }
        state.playerName = enteredName;
        state.resetForNewRun();
        startOverlay.setVisible(false);
        galleryOverlay.setVisible(false);
        hudPanel.setVisible(true);
        showScene("prologue_arrival");
    }

    private void continueGame() {
        SaveData saveData = SaveData.load(SAVE_PATH);
        if (saveData == null) {
            refreshContinueButton();
            return;
        }
        saveData.applyTo(state);
        textSpeedMs = saveData.textSpeedMs;
        nameField.setText(state.playerName);
        startOverlay.setVisible(false);
        galleryOverlay.setVisible(false);
        hudPanel.setVisible(true);
        showScene(saveData.currentSceneId == null || saveData.currentSceneId.isBlank() ? "prologue_arrival" : saveData.currentSceneId);
    }

    private void clearSaveData() {
        try {
            Files.deleteIfExists(SAVE_PATH);
        } catch (IOException ignored) {
        }
        state.reset();
        showStartScreen();
    }

    private void refreshContinueButton() {
        if (continueGameButton != null) {
            continueGameButton.setEnabled(Files.exists(SAVE_PATH));
        }
    }

    private void persistState() {
        SaveData.from(state, currentSceneId, textSpeedMs).save(SAVE_PATH);
        refreshContinueButton();
    }

    private void loadSaveData() {
        SaveData saveData = SaveData.load(SAVE_PATH);
        if (saveData == null) {
            refreshContinueButton();
            return;
        }
        saveData.applyTo(state);
        textSpeedMs = saveData.textSpeedMs;
        refreshContinueButton();
    }

    private void showEndingGallery() {
        refreshEndingGallery();
        startOverlay.setVisible(false);
        galleryOverlay.setVisible(true);
        hudPanel.setVisible(false);
        choiceOverlay.setVisible(false);
        currentScene = null;
        currentSceneId = "ending_gallery";
        activeBackgroundImage = "bg_archive_room.png";
        activeCharacterImage = "";
        titleLabel.setText("엔딩 모음");
        chapterLabel.setText("ARCHIVE");
        layoutSceneLayers();
        refreshImages();
    }

    private void refreshEndingGallery() {
        galleryListPanel.removeAll();
        for (String endingId : endingTitles.keySet()) {
            boolean unlocked = state.seenEndings.contains(endingId);
            JPanel item = new JPanel();
            item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
            item.setOpaque(true);
            item.setBackground(new Color(70, 77, 90));
            item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(111, 121, 139), 1),
                    new EmptyBorder(12, 12, 12, 12)
            ));

            JLabel name = new JLabel(unlocked ? applyPlayerName(endingTitles.get(endingId)) : "???");
            name.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
            name.setForeground(unlocked ? new Color(224, 229, 236) : ACCENT_SOFT);

            JLabel desc = new JLabel(unlocked ? applyPlayerName(endingDescriptions.get(endingId)) : "아직 확인하지 못한 엔딩");
            desc.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
            desc.setForeground(new Color(181, 188, 199));

            item.add(name);
            item.add(Box.createVerticalStrut(4));
            item.add(desc);

            galleryListPanel.add(item);
            galleryListPanel.add(Box.createVerticalStrut(10));
        }
        galleryListPanel.revalidate();
        galleryListPanel.repaint();
    }

    private void showScene(String id) {
        Scene scene = scenes.get(id);
        if (scene == null) {
            return;
        }
        currentSceneId = id;
        currentScene = scene;
        state.visitedScenes.add(id);
        if (scene.onEnter != null) {
            scene.onEnter.accept(state);
        }

        titleLabel.setText(applyPlayerName(scene.title));
        chapterLabel.setText(applyPlayerName(scene.chapter));
        activeBackgroundImage = scene.backgroundImage;
        activeCharacterImage = scene.characterImage;
        startOverlay.setVisible(false);
        galleryOverlay.setVisible(false);
        hudPanel.setVisible(true);
        if (isEndingSceneId(id)) {
            state.seenEndings.add(id);
        }
        prepareSceneFlow(scene);
        persistState();

        layoutSceneLayers();
        refreshImages();
        SwingUtilities.invokeLater(() -> {
            layoutSceneLayers();
            refreshImages();
            scenePanel.repaint();
        });
    }

    private JButton createChoiceButton(Choice choice) {
        boolean enabled = choice.isVisible(state);
        boolean completed = isChoiceCompleted(choice);
        String suffix = enabled ? "" : completed ? " [완료]" : " [잠김]";
        String label = applyPlayerName(choice.label) + suffix;
        JButton button = new JButton(asWrappedHtml(label, 24));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setFocusPainted(false);
        button.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
        button.setBackground(enabled ? BUTTON : completed ? new Color(76, 83, 96) : new Color(58, 62, 71));
        button.setForeground(enabled ? new Color(233, 236, 242) : completed ? new Color(206, 214, 226) : new Color(148, 154, 166));
        button.setBorder(choiceBorder(
                enabled ? new Color(108, 118, 136) : completed ? new Color(118, 128, 146) : new Color(86, 92, 104),
                enabled ? new Color(255, 255, 255, 24) : completed ? new Color(255, 255, 255, 18) : new Color(255, 255, 255, 10)
        ));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setEnabled(enabled);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!button.isEnabled()) {
                    return;
                }
                button.setBackground(BUTTON_HOVER);
                button.setBorder(choiceBorder(ACCENT, new Color(255, 255, 255, 32)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(enabled ? BUTTON : completed ? new Color(76, 83, 96) : new Color(58, 62, 71));
                button.setBorder(choiceBorder(
                        enabled ? new Color(108, 118, 136) : completed ? new Color(118, 128, 146) : new Color(86, 92, 104),
                        enabled ? new Color(255, 255, 255, 24) : completed ? new Color(255, 255, 255, 18) : new Color(255, 255, 255, 10)
                ));
            }
        });
        button.addActionListener(e -> {
            if (!button.isEnabled()) {
                return;
            }
            choice.effect.accept(state);
            if (choice.recordsSuspicion) {
                state.suspectHistory.add(choice.label);
            }
            persistState();
            showScene(choice.nextSceneId);
        });
        return button;
    }

    private boolean isChoiceCompleted(Choice choice) {
        return switch (choice.nextSceneId) {
            case "pool_intro" -> state.poolSolved;
            case "music_intro" -> state.musicSolved;
            case "science_intro" -> state.scienceSolved;
            case "rooftop_intro" -> state.visitedScenes.contains("rooftop_intro");
            case "final_board" -> state.visitedScenes.contains("final_board");
            default -> false;
        };
    }

    private Border choiceBorder(Color outer, Color inner) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(outer, 1),
                        BorderFactory.createLineBorder(inner, 1)
                ),
                new EmptyBorder(12, 16, 12, 16)
        );
    }

    private String buildStatusText() {
        return "truth=" + state.truthScore
                + "  suspect=" + state.suspicionScore
                + "  clues=" + state.clues.size()
                + "  solved=" + solvedCount() + "/3";
    }

    private int solvedCount() {
        int solved = 0;
        if (state.poolSolved) {
            solved++;
        }
        if (state.musicSolved) {
            solved++;
        }
        if (state.scienceSolved) {
            solved++;
        }
        return solved;
    }

    private void startDialogueAnimation(String fullText) {
        if (dialogueTimer != null && dialogueTimer.isRunning()) {
            dialogueTimer.stop();
        }
        storyArea.setText("");
        pageFullyVisible = false;
        if (fullText == null || fullText.isEmpty()) {
            pageFullyVisible = true;
            continueLabel.setText(visibleChoices.size() > 1 ? "CHOICE" : "END");
            return;
        }
        final int[] index = {0};
        dialogueTimer = new Timer(textSpeedMs, e -> {
            index[0]++;
            storyArea.setText(fullText.substring(0, index[0]));
            storyArea.setCaretPosition(storyArea.getDocument().getLength());
            if (index[0] >= fullText.length()) {
                pageFullyVisible = true;
                continueLabel.setText(nextPromptText());
                ((Timer) e.getSource()).stop();
            }
        });
        dialogueTimer.setInitialDelay(70);
        dialogueTimer.start();
    }

    private void enforceMobileAspectRatio() {
        if (adjustingFrame) {
            return;
        }
        int width = getWidth();
        int height = Math.max(getMinimumSize().height, (int) Math.round(width / MOBILE_ASPECT_RATIO));
        if (getHeight() == height) {
            return;
        }
        adjustingFrame = true;
        setSize(width, height);
        adjustingFrame = false;
    }

    private void layoutSceneLayers() {
        int width = Math.max(1, scenePanel.getWidth());
        int height = Math.max(1, scenePanel.getHeight());
        backgroundLabel.setBounds(0, 0, width, height);
        int characterWidth = Math.max(1, (int) Math.round(width * 0.92));
        int characterHeight = Math.max(1, (int) Math.round(height * 0.92));
        int characterX = (width - characterWidth) / 2;
        int characterY = Math.max(0, height - characterHeight);
        characterLabel.setBounds(characterX, characterY, characterWidth, characterHeight);
        overlayPanel.setBounds(0, 0, width, height);
        startOverlay.setBounds(0, 0, width, height);
        galleryOverlay.setBounds(0, 0, width, height);
        scenePanel.setComponentZOrder(backgroundLabel, 4);
        scenePanel.setComponentZOrder(characterLabel, 3);
        scenePanel.setComponentZOrder(overlayPanel, 2);
        scenePanel.setComponentZOrder(galleryOverlay, 1);
        scenePanel.setComponentZOrder(startOverlay, 0);
    }

    private void refreshImages() {
        int width = Math.max(1, scenePanel.getWidth());
        int height = Math.max(1, scenePanel.getHeight());
        String backgroundImage = activeBackgroundImage == null ? "" : activeBackgroundImage;
        String characterImage = activeCharacterImage == null ? "" : activeCharacterImage;
        setImage(backgroundLabel, backgroundImage, width, height, backgroundImage.isEmpty() ? "" : "BG: " + backgroundImage, true);
        setImage(characterLabel, characterImage, (int) Math.round(width * 0.92), (int) Math.round(height * 0.92),
                characterImage.isEmpty() ? "" : "CH: " + characterImage, false);
    }

    private void setImage(JLabel label, String fileName, int targetWidth, int targetHeight, String fallbackText, boolean cover) {
        if (fileName == null || fileName.isEmpty()) {
            label.setIcon(null);
            label.setText(fallbackText == null ? "" : fallbackText);
            return;
        }
        try {
            BufferedImage image = loadImageCached(fileName);
            if (image == null) {
                label.setIcon(null);
                label.setText(fallbackText);
                return;
            }
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            double scale = cover
                    ? Math.max((double) targetWidth / imageWidth, (double) targetHeight / imageHeight)
                    : Math.min((double) targetWidth / imageWidth, (double) targetHeight / imageHeight);
            int scaledWidth = Math.max(1, (int) Math.round(imageWidth * scale));
            int scaledHeight = Math.max(1, (int) Math.round(imageHeight * scale));
            Image scaled = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            label.setIcon(new ImageIcon(scaled));
            label.setText("");
        } catch (IOException ex) {
            label.setIcon(null);
            label.setText(fallbackText);
        }
    }

    private BufferedImage loadImageCached(String fileName) throws IOException {
        BufferedImage cached = imageCache.get(fileName);
        if (cached != null) {
            return cached;
        }
        String path = "assets/images/" + fileName;
        BufferedImage image = ImageIO.read(new File(path));
        if (image != null) {
            imageCache.put(fileName, image);
        }
        return image;
    }

    private void prepareSceneFlow(Scene scene) {
        visibleChoices = collectVisibleChoices(scene);
        scenePages = buildPages(scene);
        currentPageIndex = 0;
        choiceOverlay.setVisible(false);
        choicesPanel.removeAll();
        if (scenePages.isEmpty()) {
            storyArea.setText("");
            pageFullyVisible = true;
            continueLabel.setText(nextPromptText());
            handleSceneEnd();
            return;
        }
        showCurrentPage();
    }

    private List<Choice> collectVisibleChoices(Scene scene) {
        List<Choice> choices = new ArrayList<>();
        for (Choice choice : scene.choices) {
            if (choice.isVisible(state)) {
                choices.add(choice);
            }
        }
        return choices;
    }

    private List<PageEntry> buildPages(Scene scene) {
        List<PageEntry> pages = new ArrayList<>();
        if (scene.narration != null && !scene.narration.isBlank()) {
            pages.addAll(splitIntoPages(scene.narration, ""));
        }
        if (scene.dialogue != null && !scene.dialogue.isBlank()) {
            pages.addAll(splitIntoPages(scene.dialogue, scene.speaker));
        }
        return pages;
    }

    private List<PageEntry> splitIntoPages(String text, String speaker) {
        List<PageEntry> pages = new ArrayList<>();
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(applyPlayerName(text).strip());
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!token.isEmpty()) {
                sentences.add(token);
            }
        }

        if (sentences.isEmpty()) {
            return pages;
        }

        StringBuilder page = new StringBuilder();
        int sentenceCount = 0;
        for (String sentence : sentences) {
            int projectedLength = page.length() + sentence.length() + (page.length() > 0 ? 1 : 0);
            if (page.length() > 0 && (sentenceCount >= 2 || projectedLength > MAX_PAGE_CHARS)) {
                pages.add(new PageEntry(page.toString(), speaker == null ? "" : speaker.strip()));
                page.setLength(0);
                sentenceCount = 0;
            }
            if (page.length() > 0) {
                page.append(' ');
            }
            page.append(sentence);
            sentenceCount++;
            if (sentenceCount == 2 && page.length() >= MAX_PAGE_CHARS / 2) {
                pages.add(new PageEntry(page.toString(), speaker == null ? "" : speaker.strip()));
                page.setLength(0);
                sentenceCount = 0;
            }
        }

        if (page.length() > 0) {
            pages.add(new PageEntry(page.toString(), speaker == null ? "" : speaker.strip()));
        }
        return pages;
    }

    private String applyPlayerName(String text) {
        String name = state.playerName == null || state.playerName.isBlank() ? DEFAULT_PLAYER_NAME : state.playerName.strip();
        return text.replace("플레이어", name);
    }

    private void showCurrentPage() {
        currentPage = scenePages.get(currentPageIndex);
        updateSpeakerBadge();
        continueLabel.setText(pageFullyVisible ? nextPromptText() : "...");
        startDialogueAnimation(currentPage.text());
    }

    private void advanceStory() {
        if (choiceOverlay.isVisible()) {
            return;
        }
        if (dialogueTimer != null && dialogueTimer.isRunning() && !pageFullyVisible) {
            revealCurrentPageImmediately();
            return;
        }
        if (currentPageIndex + 1 < scenePages.size()) {
            currentPageIndex++;
            showCurrentPage();
            return;
        }
        handleSceneEnd();
    }

    private void revealCurrentPageImmediately() {
        if (dialogueTimer != null && dialogueTimer.isRunning()) {
            dialogueTimer.stop();
        }
        storyArea.setText(currentPage.text());
        storyArea.setCaretPosition(storyArea.getDocument().getLength());
        pageFullyVisible = true;
        continueLabel.setText(nextPromptText());
    }

    private void handleSceneEnd() {
        if (isEndingScene()) {
            showRestartOverlay();
            return;
        }
        if (currentScene == null || currentScene.choices.isEmpty()) {
            continueLabel.setText("END");
            return;
        }
        showChoicesOverlay();
    }

    private void showChoicesOverlay() {
        choicesPanel.removeAll();
        for (Choice choice : currentScene.choices) {
            choicesPanel.add(createChoiceButton(choice));
        }
        choicesPanel.revalidate();
        choicesPanel.repaint();
        choiceOverlay.setVisible(true);
        continueLabel.setText("CHOICE");
    }

    private void showRestartOverlay() {
        choicesPanel.removeAll();
        JButton restartButton = createMenuButton("다시 시작", this::showStartScreen);
        restartButton.setHorizontalAlignment(SwingConstants.CENTER);
        choicesPanel.add(restartButton);
        choicesPanel.revalidate();
        choicesPanel.repaint();
        choiceOverlay.setVisible(true);
        continueLabel.setText("RESTART");
    }

    private void updateSpeakerBadge() {
        if (currentScene == null) {
            speakerBadge.setText(" ");
            speakerBadge.setVisible(false);
            return;
        }
        String speaker = currentPage.speaker();
        boolean hasSpeaker = speaker != null && !speaker.isBlank();
        speakerBadge.setText(hasSpeaker ? applyPlayerName(speaker.strip()) : " ");
        speakerBadge.setVisible(hasSpeaker);
    }

    private boolean isEndingScene() {
        return isEndingSceneId(currentSceneId);
    }

    private boolean isEndingSceneId(String id) {
        return id != null && id.startsWith("ending_");
    }

    private String asWrappedHtml(String text, int widthEm) {
        String safe = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
        return "<html><div style='width:" + widthEm + "em;'>" + safe + "</div></html>";
    }

    private String nextPromptText() {
        if (currentPageIndex + 1 < scenePages.size()) {
            return "NEXT";
        }
        if (currentScene != null && !currentScene.choices.isEmpty()) {
            return "CHOICE";
        }
        return "END";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }

    private static class GameState {
        boolean poolSolved;
        boolean musicSolved;
        boolean scienceSolved;
        int truthScore;
        int suspicionScore;
        String finalChoice = "";
        String playerName = DEFAULT_PLAYER_NAME;
        final Set<String> seenEndings = new LinkedHashSet<>();
        final Set<String> clues = new LinkedHashSet<>();
        final Set<String> visitedScenes = new LinkedHashSet<>();
        final List<String> suspectHistory = new ArrayList<>();
        final Map<String, String> evidenceBoard = new LinkedHashMap<>();

        void unlockClue(String clueName) {
            if (clues.add(clueName)) {
                evidenceBoard.put(clueName, "확보");
            }
        }

        void resetForNewRun() {
            poolSolved = false;
            musicSolved = false;
            scienceSolved = false;
            truthScore = 0;
            suspicionScore = 0;
            finalChoice = "";
            clues.clear();
            visitedScenes.clear();
            suspectHistory.clear();
            evidenceBoard.clear();
        }

        void reset() {
            resetForNewRun();
            playerName = DEFAULT_PLAYER_NAME;
            seenEndings.clear();
        }
    }

    private static class Scene {
        final String title;
        final String chapter;
        final String narration;
        final String speaker;
        final String dialogue;
        final String backgroundImage;
        final String characterImage;
        final List<Choice> choices;
        final String bgm;
        final String sfx;
        final String transition;
        final Consumer<GameState> onEnter;

        Scene(String title, String chapter, String narration, String speaker, String dialogue,
              String backgroundImage, String characterImage, List<Choice> choices, Consumer<GameState> onEnter) {
            this(title, chapter, narration, speaker, dialogue, backgroundImage, characterImage, choices, "ambient_night", "", "fade", onEnter);
        }

        Scene(String title, String chapter, String narration, String speaker, String dialogue,
              String backgroundImage, String characterImage, List<Choice> choices,
              String bgm, String sfx, String transition, Consumer<GameState> onEnter) {
            this.title = title;
            this.chapter = chapter;
            this.narration = narration;
            this.speaker = speaker;
            this.dialogue = dialogue;
            this.backgroundImage = backgroundImage;
            this.characterImage = characterImage;
            this.choices = choices;
            this.bgm = bgm;
            this.sfx = sfx;
            this.transition = transition;
            this.onEnter = onEnter;
        }
    }

    private static class Choice {
        final String label;
        final String nextSceneId;
        final Consumer<GameState> effect;
        final Predicate<GameState> visibility;
        final boolean recordsSuspicion;

        Choice(String label, String nextSceneId) {
            this(label, nextSceneId, g -> {}, g -> true, false);
        }

        Choice(String label, String nextSceneId, Consumer<GameState> effect) {
            this(label, nextSceneId, effect, g -> true, false);
        }

        Choice(String label, String nextSceneId, Consumer<GameState> effect, Predicate<GameState> visibility) {
            this(label, nextSceneId, effect, visibility, false);
        }

        Choice(String label, String nextSceneId, Consumer<GameState> effect, Predicate<GameState> visibility, boolean recordsSuspicion) {
            this.label = label;
            this.nextSceneId = nextSceneId;
            this.effect = effect;
            this.visibility = visibility;
            this.recordsSuspicion = recordsSuspicion;
        }

        boolean isVisible(GameState state) {
            return visibility.test(state);
        }
    }

    private record PageEntry(String text, String speaker) {
        private static PageEntry empty() {
            return new PageEntry("", "");
        }
    }

    private static class SaveData {
        String playerName = DEFAULT_PLAYER_NAME;
        String currentSceneId = "prologue_arrival";
        int textSpeedMs = 18;
        boolean poolSolved;
        boolean musicSolved;
        boolean scienceSolved;
        int truthScore;
        int suspicionScore;
        String finalChoice = "";
        final Set<String> seenEndings = new LinkedHashSet<>();
        final Set<String> clues = new LinkedHashSet<>();
        final Set<String> visitedScenes = new LinkedHashSet<>();
        final List<String> suspectHistory = new ArrayList<>();
        final Map<String, String> evidenceBoard = new LinkedHashMap<>();

        static SaveData from(GameState state, String currentSceneId, int textSpeedMs) {
            SaveData data = new SaveData();
            data.playerName = state.playerName;
            data.currentSceneId = currentSceneId;
            data.textSpeedMs = textSpeedMs;
            data.poolSolved = state.poolSolved;
            data.musicSolved = state.musicSolved;
            data.scienceSolved = state.scienceSolved;
            data.truthScore = state.truthScore;
            data.suspicionScore = state.suspicionScore;
            data.finalChoice = state.finalChoice;
            data.seenEndings.addAll(state.seenEndings);
            data.clues.addAll(state.clues);
            data.visitedScenes.addAll(state.visitedScenes);
            data.suspectHistory.addAll(state.suspectHistory);
            data.evidenceBoard.putAll(state.evidenceBoard);
            return data;
        }

        void applyTo(GameState state) {
            state.resetForNewRun();
            state.playerName = playerName == null ? DEFAULT_PLAYER_NAME : playerName;
            state.poolSolved = poolSolved;
            state.musicSolved = musicSolved;
            state.scienceSolved = scienceSolved;
            state.truthScore = truthScore;
            state.suspicionScore = suspicionScore;
            state.finalChoice = finalChoice == null ? "" : finalChoice;
            state.seenEndings.addAll(seenEndings);
            state.clues.addAll(clues);
            state.visitedScenes.addAll(visitedScenes);
            state.suspectHistory.addAll(suspectHistory);
            state.evidenceBoard.putAll(evidenceBoard);
        }

        void save(Path path) {
            try {
                Files.createDirectories(path.getParent());
                Properties properties = new Properties();
                properties.setProperty("playerName", playerName == null ? "" : playerName);
                properties.setProperty("currentSceneId", currentSceneId == null ? "prologue_arrival" : currentSceneId);
                properties.setProperty("textSpeedMs", Integer.toString(textSpeedMs));
                properties.setProperty("poolSolved", Boolean.toString(poolSolved));
                properties.setProperty("musicSolved", Boolean.toString(musicSolved));
                properties.setProperty("scienceSolved", Boolean.toString(scienceSolved));
                properties.setProperty("truthScore", Integer.toString(truthScore));
                properties.setProperty("suspicionScore", Integer.toString(suspicionScore));
                properties.setProperty("finalChoice", finalChoice == null ? "" : finalChoice);
                properties.setProperty("seenEndings", String.join("|", seenEndings));
                properties.setProperty("clues", String.join("|", clues));
                properties.setProperty("visitedScenes", String.join("|", visitedScenes));
                properties.setProperty("suspectHistory", String.join("|", suspectHistory));
                for (Map.Entry<String, String> entry : evidenceBoard.entrySet()) {
                    properties.setProperty("evidence." + entry.getKey(), entry.getValue());
                }
                try (var writer = Files.newBufferedWriter(path)) {
                    properties.store(writer, "choice save data");
                }
            } catch (IOException ignored) {
            }
        }

        static SaveData load(Path path) {
            if (!Files.exists(path)) {
                return null;
            }
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            } catch (IOException ex) {
                return null;
            }

            SaveData data = new SaveData();
            data.playerName = properties.getProperty("playerName", DEFAULT_PLAYER_NAME);
            data.currentSceneId = properties.getProperty("currentSceneId", "prologue_arrival");
            data.textSpeedMs = Integer.parseInt(properties.getProperty("textSpeedMs", "18"));
            data.poolSolved = Boolean.parseBoolean(properties.getProperty("poolSolved", "false"));
            data.musicSolved = Boolean.parseBoolean(properties.getProperty("musicSolved", "false"));
            data.scienceSolved = Boolean.parseBoolean(properties.getProperty("scienceSolved", "false"));
            data.truthScore = Integer.parseInt(properties.getProperty("truthScore", "0"));
            data.suspicionScore = Integer.parseInt(properties.getProperty("suspicionScore", "0"));
            data.finalChoice = properties.getProperty("finalChoice", "");
            addAll(properties.getProperty("seenEndings", ""), data.seenEndings);
            addAll(properties.getProperty("clues", ""), data.clues);
            addAll(properties.getProperty("visitedScenes", ""), data.visitedScenes);
            addAll(properties.getProperty("suspectHistory", ""), data.suspectHistory);
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("evidence.")) {
                    data.evidenceBoard.put(key.substring("evidence.".length()), properties.getProperty(key, ""));
                }
            }
            return data;
        }

        private static void addAll(String encoded, Set<String> target) {
            if (encoded == null || encoded.isBlank()) {
                return;
            }
            for (String value : encoded.split("\\|")) {
                if (!value.isBlank()) {
                    target.add(value);
                }
            }
        }

        private static void addAll(String encoded, List<String> target) {
            if (encoded == null || encoded.isBlank()) {
                return;
            }
            for (String value : encoded.split("\\|")) {
                if (!value.isBlank()) {
                    target.add(value);
                }
            }
        }
    }

    private static class LengthFilter extends DocumentFilter {
        private final int maxLength;

        LengthFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) {
                return;
            }
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String incoming = text == null ? "" : text;
            int currentLength = fb.getDocument().getLength();
            int newLength = currentLength - length + incoming.length();
            if (newLength <= maxLength) {
                super.replace(fb, offset, length, incoming, attrs);
                return;
            }

            int allowed = maxLength - (currentLength - length);
            if (allowed > 0) {
                super.replace(fb, offset, length, incoming.substring(0, allowed), attrs);
            }
        }
    }
}
