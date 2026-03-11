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
        endingTitles.put("ending_true", "기록된 진실");
        endingTitles.put("ending_wrong_accusation", "희생양");
        endingTitles.put("ending_silence", "미완의 보고서");

        endingDescriptions.put("ending_true", "무리한 촬영과 안전 부재가 만든 연속 사고를 밝혀낸 결말");
        endingDescriptions.put("ending_wrong_accusation", "김현진에게 죄를 뒤집어씌우고 구조적 원인을 놓친 결말");
        endingDescriptions.put("ending_silence", "진실 직전에서 멈춰 프로젝트의 책임만 흐려진 결말");
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
                월야고등학교에서는 전국 청소년 UCC 공모전을 위해 프로젝트 팀이 꾸려졌다.
                지도교사 양지영 아래에서 학생들은 기획, 촬영, 음악, 장치 제작, 수중 촬영 역할을 나눠 맡았다.
                그러나 촬영 기간 동안 음악실, 과학실, 수영장에서 연달아 세 번의 사고가 발생했고, 학교는 모두 사고로 처리했다.
                """,
                "교장",
                "공모전 준비 팀에서만 세 건의 사망 사고가 났습니다. 공식 기록은 사고지만, 학생들은 누군가 팀을 노렸다고 믿고 있어요.",
                "bg_school_gate_night.png",
                "ch_principal.png",
                List.of(new Choice("프로젝트 기록을 확인한다", "prologue_principal")),
                null
        ));

        scenes.put("prologue_principal", new Scene(
                "프로젝트 개요",
                "Chapter 1  프롤로그",
                """
                팀의 주제는 '청소년의 도전과 성장'이었다.
                양지영은 전체 연출을, 방송부 김태형은 촬영과 장비를, 학생회 김현진은 일정과 운영을 맡았다.
                음악부 주다영, 과학부 한승준, 수영부 김준영, 체육부 장훈은 각자의 장면을 준비하며 촬영을 이어 갔다.
                """,
                "플레이어",
                "사건은 서로 다른 장소에서 일어났지만, 출발점은 같은 프로젝트 안에 있다.",
                "bg_archive_room.png",
                "ch_exorcist.png",
                List.of(new Choice("양지영에게 당시 상황을 듣는다", "prologue_nurse")),
                null
        ));

        scenes.put("prologue_nurse", new Scene(
                "양지영의 증언",
                "Chapter 1  프롤로그",
                """
                양지영은 촬영이 갈수록 무리해졌다고 말한다.
                공모전 마감이 다가오자 재촬영과 테스트가 겹쳤고, 학생들은 안전 확인보다 결과를 먼저 챙기기 시작했다.
                그 와중에 일정 관리 담당이던 김현진은 여러 차례 촬영 중단을 말했지만, 사건 뒤에는 오히려 가장 많이 의심받는 인물이 되었다.
                """,
                "양지영",
                "현진이는 막으려고 했어요. 그런데 끝내 막지 못했고, 그 죄책감 때문에 더 말을 못 하게 됐죠.",
                "bg_main_hall_night.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("프로젝트 사고 기록을 조사한다", "prologue_hall")),
                null
        ));

        scenes.put("prologue_hall", new Scene(
                "사고 기록 복도",
                "Chapter 1  프롤로그",
                """
                학교가 정리한 자료에는 세 사고가 따로 적혀 있다.
                하지만 장소만 다를 뿐 모두 촬영 준비 중이었고, 김현진과 장비 기록, 일정표가 늘 근처에 남아 있다.
                공통점은 너무 선명해서 누군가를 범인으로 상상하기 쉽게 만든다.
                """,
                "플레이어",
                "같은 팀, 같은 마감, 같은 조급함. 연결점이 많다는 사실이 곧 살인의 증거는 아니다.",
                "bg_main_hall_night.png",
                "ch_exorcist.png",
                List.of(new Choice("사건 정리로 간다", "case_hub")),
                null
        ));

        scenes.put("pool_intro", new Scene(
                "수영장 사고",
                "Chapter 4  세 번째 사고",
                """
                세 번째 사고의 피해자는 수영부 김준영이다.
                UCC의 도전 장면을 위해 수중 촬영을 준비하던 중, 준영은 익사 사고를 당했다.
                수영부 학생이 물에서 죽었다는 사실과 일부 촬영 영상의 공백 때문에 학생들은 마침내 누군가 팀을 노리고 있다고 확신하기 시작한다.
                """,
                "양지영",
                "준영이는 체력이 좋았어요. 그래서 다들 더더욱 사고라는 말을 못 믿었죠.",
                "bg_pool_night.png",
                "ch_yang_jiyeong.png",
                List.of(
                        new Choice("수중 촬영 영상과 일정표를 본다", "pool_video")
                ),
                null
        ));

        scenes.put("pool_video", new Scene(
                "수중 촬영 영상",
                "Chapter 4  세 번째 사고",
                """
                영상에는 수면이 갑자기 크게 흔들리는 장면과 구조가 늦어진 몇 초의 공백이 남아 있다.
                촬영 장비 위치가 예상보다 깊은 쪽으로 옮겨져 있었고, 보조 인력 배치표는 마지막 순간 손으로 덧고쳐져 있다.
                학생들은 김현진이 그날 일정과 배치를 다시 정리했다는 사실을 들어 의심을 키운다.
                """,
                "플레이어",
                "배치표를 수정한 사람과 사고를 만든 사람이 같다는 뜻은 아니다. 하지만 오해는 늘 그런 식으로 자란다.",
                "bg_pool_edge.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("장훈과 당시 현장 지원 기록을 확인한다", "pool_interview"),
                        new Choice("단서를 정리한다", "pool_review", g -> {}, Main::canReviewPool)
                ),
                g -> g.unlockClue("수영장 영상")
        ));

        scenes.put("pool_interview", new Scene(
                "현장 지원 기록",
                "Chapter 4  세 번째 사고",
                """
                장훈의 진술과 현장 지원표에는 원래 두 명이 있어야 할 안전 보조가 한 명으로 줄어 있었던 정황이 남아 있다.
                김현진은 인원 부족 때문에 촬영을 미루자고 했지만, 이미 대관 시간과 공모전 마감이 겹쳐 팀은 촬영을 강행했다.
                공백처럼 보였던 영상 일부는 물에 젖은 장비를 급히 정리하는 과정에서 끊긴 것이었다.
                """,
                "플레이어",
                "세 번째 사고에서조차 보이는 건 살인의 흔적보다 무너진 통제다.",
                "bg_pool_night.png",
                "ch_exorcist.png",
                List.of(new Choice("단서를 정리한다", "pool_review", g -> {}, Main::canReviewPool)),
                g -> g.unlockClue("현장 지원 기록")
        ));

        scenes.put("pool_review", new Scene(
                "수영장 단서 정리",
                "Chapter 4  세 번째 사고",
                """
                수영 실력이 좋았던 준영의 죽음은 그래서 더 누군가의 의도처럼 보인다.
                하지만 영상 공백, 수정된 배치표, 줄어든 안전 보조 인원은 모두 촬영을 멈추지 못한 팀의 조급함과 연결된다.
                세 번째 사고는 의심을 완성한 사건이지만, 동시에 세 사고의 공통 원인도 가장 분명하게 드러낸다.
                """,
                "플레이어",
                "이제는 사람 하나의 악의보다 프로젝트 전체의 무리함을 설명할 수 있어야 한다.",
                "bg_pool_night.png",
                "ch_exorcist.png",
                List.of(new Choice("결론을 내린다", "pool_deduction")),
                null
        ));

        scenes.put("pool_deduction", new Scene(
                "수영장 추리",
                "Chapter 4  세 번째 사고",
                """
                단서 조합:
                수중 촬영 영상 / 수정된 배치표 / 안전 보조 공백 / 장훈의 진술
                무엇이 가장 일관된 설명인가.
                """,
                "플레이어",
                "세 번째 사고의 결론이 곧 세 사건 전체의 방향을 정한다.",
                "bg_pool_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("김현진이 배치를 바꿔 김준영을 위험하게 만들었다고 본다", "pool_wrong", g -> g.suspicionScore++, g -> true, true),
                        new Choice("무리한 수중 촬영과 부족한 안전 인력 속에서 벌어진 익사 사고로 본다", "pool_true", g -> {
                            g.poolSolved = true;
                            g.truthScore++;
                            g.unlockClue("김준영 사건 해결");
                        })
                ),
                null
        ));

        scenes.put("pool_wrong", new Scene(
                "수영장 오판",
                "Chapter 4  세 번째 사고",
                """
                배치표를 고친 사람이 김현진이라는 사실은 마지막 의심을 완성한다.
                하지만 기록 전체는 그 수정이 사고를 만들기 위한 조작이 아니라, 이미 부족해진 인원을 어떻게든 맞추려던 임시 대응이었음을 보여 준다.
                범인을 세우는 데 성공해도 프로젝트의 구조적 실패는 여전히 설명되지 않는다.
                """,
                "플레이어",
                "누군가를 찍어내는 순간, 모두가 조금씩 밀어 넣은 위험은 이름을 잃는다.",
                "bg_pool_edge.png",
                "ch_exorcist.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("pool_true", new Scene(
                "수영장 사건 결론",
                "Chapter 4  세 번째 사고",
                """
                김준영의 익사는 개인의 실수 하나로 생긴 사고가 아니었다.
                일정에 쫓겨 안전 보조 인원이 줄고, 촬영 구도만 맞춘 채 수중 테스트를 강행한 결과였다.
                수영을 잘하는 학생이라도 무리한 촬영 구조와 늦은 대응 앞에서는 예외가 될 수 없었다.
                """,
                "양지영",
                "이제 세 사건이 왜 닮았는지 설명할 수 있겠네요. 누군가가 죽이고 다녀서가 아니라, 같은 방식으로 위험을 밀어붙였기 때문이에요.",
                "bg_pool_edge.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("music_intro", new Scene(
                "음악실 사고",
                "Chapter 2  첫 번째 사고",
                """
                첫 사고의 피해자는 음악부 소속 주다영이다.
                UCC 영상의 배경 음악과 연주 장면을 준비하던 밤, 피아노 덮개가 강하게 닫히며 손가락이 끼는 사고가 일어났다.
                균형을 잃고 넘어진 주다영은 머리를 부딪혀 사망했고, 복도에서 들린 발걸음 소리 때문에 누군가 밀었다는 소문이 퍼졌다.
                """,
                "양지영",
                "다영이는 곡을 다시 녹음하겠다고 혼자 남았어요. 그 뒤로는 피아노 소리랑 비명만 남았죠.",
                "bg_music_room_night.png",
                "ch_yang_jiyeong.png",
                List.of(
                        new Choice("녹음 파일과 현장 기록을 본다", "music_record")
                ),
                null
        ));

        scenes.put("music_record", new Scene(
                "녹음 파일과 체크리스트",
                "Chapter 2  첫 번째 사고",
                """
                파일에는 연주, 급하게 일어나는 의자 소리, 문 닫힘, 피아노 덮개 충격음이 차례로 남아 있다.
                김현진은 그 시간 음악실 복도에서 다음 날 촬영 순서를 정리하고 있었고, 열린 문을 닫았다고 진술했다.
                촬영 체크리스트에는 '야간 단독 연습 금지'가 적혀 있었지만 실제로는 지켜지지 않았다.
                """,
                "플레이어",
                "발걸음과 문 닫힘은 수상하다. 하지만 그보다 먼저 봐야 할 건 주의사항이 왜 무시됐는지다.",
                "bg_music_room_close.png",
                "ch_exorcist.png",
                List.of(new Choice("단서를 정리한다", "music_review")),
                g -> g.unlockClue("음악실 녹음")
        ));

        scenes.put("music_review", new Scene(
                "음악실 단서 정리",
                "Chapter 2  첫 번째 사고",
                """
                발걸음 소리 하나만 떼어 놓으면 누군가 침입한 장면처럼 읽힌다.
                하지만 녹음의 순서와 체크리스트를 함께 보면, 촬영을 서두르다 안전 수칙이 무너진 상태에서 사고가 먼저 일어났고 김현진은 그 직후 현장 근처에 있었던 쪽에 가깝다.
                첫 사고는 누가 다영을 노렸는지보다, 왜 다영이 혼자 위험한 연습을 하게 되었는지를 묻는다.
                """,
                "플레이어",
                "가까이 있었다는 이유만으로 원인을 사람에게 돌리면, 구조는 금방 가려진다.",
                "bg_music_room_night.png",
                "ch_exorcist.png",
                List.of(new Choice("결론을 내린다", "music_deduction")),
                null
        ));

        scenes.put("music_deduction", new Scene(
                "음악실 추리",
                "Chapter 2  첫 번째 사고",
                """
                단서 조합:
                연습 녹음 / 문 닫힘 / 복도 발걸음 / 야간 단독 연습 금지 체크리스트
                무엇이 가장 일관된 설명인가.
                """,
                "플레이어",
                "수상한 동선보다 먼저 봐야 할 건, 사고가 날 수밖에 없던 준비 과정이다.",
                "bg_music_room_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("김현진이 음악실에 들어와 다영을 해쳤다고 본다", "music_wrong", g -> g.suspicionScore++, g -> true, true),
                        new Choice("무리한 야간 연습과 피아노 덮개 사고가 겹친 사고로 본다", "music_true", g -> {
                            g.musicSolved = true;
                            g.truthScore++;
                            g.unlockClue("주다영 사건 해결");
                        })
                ),
                null
        ));

        scenes.put("music_wrong", new Scene(
                "음악실 오판",
                "Chapter 2  첫 번째 사고",
                """
                김현진이 복도에 있었다는 사실은 강한 의심을 만든다.
                그러나 녹음의 순서와 체크리스트는 사고 뒤 현장에 가까이 갔을 가능성만 보여 줄 뿐, 다영을 해쳤다는 증거는 되지 못한다.
                범인을 한 명 세우는 순간, 촬영을 방치한 팀 전체의 책임은 빠르게 흐려진다.
                """,
                "플레이어",
                "설명은 쉬워졌지만, 정작 사고가 왜 가능했는지는 더 보이지 않게 됐다.",
                "bg_music_room_night.png",
                "ch_exorcist.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("music_true", new Scene(
                "음악실 사건 결론",
                "Chapter 2  첫 번째 사고",
                """
                주다영은 야간 연습과 촬영을 혼자 이어 가다 피아노 덮개 사고를 당했다.
                손이 끼는 순간 중심을 잃고 넘어졌고, 머리를 강하게 부딪친 것이 직접 원인이었다.
                문 닫힘과 발걸음은 사고 직후의 흔적이었고, 학생들이 그것을 뒤늦게 살인 장면처럼 해석하면서 첫 오해가 만들어졌다.
                """,
                "양지영",
                "그날 필요한 건 더 좋은 장면이 아니라 멈추는 판단이었어요.",
                "bg_music_room_close.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("science_intro", new Scene(
                "과학실 사고",
                "Chapter 3  두 번째 사고",
                """
                두 번째 사고의 피해자는 과학부 한승준이다.
                승준은 촬영용 실험 장치와 특수 효과 소품을 테스트하던 중 폭발 사고를 당했다.
                기록에는 장치 반응이 예상보다 강했다고만 적혀 있지만, 학생들은 누군가 약품이나 부품을 건드렸다고 수군댄다.
                """,
                "플레이어",
                "첫 사고 뒤에도 촬영은 멈추지 않았다. 그래서 두 번째 사고는 더 무겁다.",
                "bg_science_lab_night.png",
                "ch_ghost_han_seungjun.png",
                List.of(
                        new Choice("장치 기록과 실험 메모를 조사한다", "science_lab")
                ),
                null
        ));

        scenes.put("science_lab", new Scene(
                "장치 기록과 실험 메모",
                "Chapter 3  두 번째 사고",
                """
                테스트 장비 옆에는 반쯤 지워진 라벨, 다시 적은 반응식, 그리고 촬영용 효과 장치 배치도가 남아 있다.
                일정표에는 본 촬영 전까지 장치를 완성해야 한다는 메모가 반복되어 있고, 안전 점검 칸은 여러 번 비어 있다.
                김현진은 테스트 연기를 제안했지만, 팀은 공모전 마감 전에 장면을 완성해야 한다며 강행했다.
                """,
                "플레이어",
                "누군가 손댄 흔적으로도 읽히지만, 급하게 덧씌운 수정 흔적이라는 해석도 충분하다.",
                "bg_science_explosion_mark.png",
                "ch_exorcist.png",
                List.of(new Choice("단서를 정리한다", "science_review")),
                g -> g.unlockClue("라벨 지워진 용기")
        ));

        scenes.put("science_review", new Scene(
                "과학실 단서 정리",
                "Chapter 3  두 번째 사고",
                """
                폭발은 누군가 일부러 꾸민 장면처럼 보이기 쉽다.
                하지만 장치 메모, 비어 있는 안전 점검, 촉박한 일정표를 겹쳐 놓으면 이 사고는 조작보다 방치와 강행의 결과에 가깝다.
                한 사람의 악의보다 여러 사람의 조급함이 더 큰 힘으로 현장을 밀어붙이고 있었다.
                """,
                "플레이어",
                "두 번째 사고는 범인의 손보다 멈추지 못한 팀의 관성을 더 또렷하게 남긴다.",
                "bg_science_lab_night.png",
                "ch_exorcist.png",
                List.of(new Choice("결론을 내린다", "science_deduction")),
                null
        ));

        scenes.put("science_deduction", new Scene(
                "과학실 추리",
                "Chapter 3  두 번째 사고",
                """
                단서 조합:
                수정된 반응식 / 라벨 지워진 용기 / 비어 있는 안전 점검 / 연기 요청 기록
                이 사건의 원인은 무엇인가.
                """,
                "플레이어",
                "누군가 손댔다고 믿으면 편해진다. 하지만 그 편안함이 진실과는 다를 수 있다.",
                "bg_science_lab_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("김현진이 장치를 조작했다고 본다", "science_wrong", g -> g.suspicionScore++, g -> true, true),
                        new Choice("촉박한 일정 속 안전 점검이 무너진 장치 사고로 본다", "science_true", g -> {
                            g.scienceSolved = true;
                            g.truthScore++;
                            g.unlockClue("한승준 사건 해결");
                        })
                ),
                null
        ));

        scenes.put("science_wrong", new Scene(
                "과학실 오판",
                "Chapter 3  두 번째 사고",
                """
                지워진 라벨과 수정된 메모는 누군가 일부러 손댄 흔적처럼 읽힌다.
                그러나 그 흔적은 일정에 쫓겨 반복된 임시 수정과 누락된 확인 절차만으로도 설명된다.
                조작이라는 결론은 선명하지만, 왜 위험한 테스트가 계속되었는지는 끝내 설명하지 못한다.
                """,
                "플레이어",
                "악의를 하나 상정하는 순간, 모두가 알고도 넘긴 경고는 사라져 버린다.",
                "bg_science_explosion_mark.png",
                "ch_exorcist.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("science_true", new Scene(
                "과학실 사건 결론",
                "Chapter 3  두 번째 사고",
                """
                한승준은 촬영용 장치를 급하게 완성하는 과정에서 충분한 안전 점검 없이 테스트를 강행했다.
                수정된 기록과 반쯤 지워진 라벨은 조작이 아니라 반복된 임시 조치의 흔적이었다.
                첫 사고 뒤에도 프로젝트를 멈추지 못한 팀의 선택이 두 번째 사고를 더 쉽게 만들었다.
                """,
                "양지영",
                "여기까지 왔으면 멈췄어야 했어요. 그런데 다들 한 번만 더 하면 된다고 생각했죠.",
                "bg_science_lab_night.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("case_hub", new Scene(
                "사건 정리",
                "Chapter 1-4  프로젝트 조사",
                """
                세 사고는 모두 같은 UCC 프로젝트 안에서 일어났다.
                음악실, 과학실, 수영장. 장소는 달랐지만 일정 압박, 미흡한 안전 확인, 그리고 김현진을 향한 의심이 반복된다.
                플레이어는 사건들을 한 사람의 범행으로 묶을지, 프로젝트 전체의 실패로 읽을지 스스로 정리해야 한다.
                """,
                "플레이어",
                "연결점이 많을수록 사람들은 범인을 찾고 싶어 한다. 하지만 연결점이 곧 원인의 전부는 아니다.",
                "bg_main_hall_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("음악실 사고", "music_intro", g -> {}, g -> !g.musicSolved),
                        new Choice("과학실 사고", "science_intro", g -> {}, g -> g.musicSolved && !g.scienceSolved),
                        new Choice("수영장 사고", "pool_intro", g -> {}, g -> g.musicSolved && g.scienceSolved && !g.poolSolved),
                        new Choice("최종 보고", "rooftop_intro", g -> {}, g -> g.musicSolved && g.scienceSolved && g.poolSolved && !g.visitedScenes.contains("rooftop_intro"))
                ),
                null
        ));

        scenes.put("rooftop_intro", new Scene(
                "최종 보고 준비",
                "Chapter 5  프로젝트 결론",
                """
                세 사고를 모두 정리하고 나면, 남는 질문은 하나다.
                김현진은 세 사건 모두 가까이에 있었고 일정표와 배치표에 이름이 남아 있다.
                그러나 동시에 촬영 중단과 연기를 요청한 기록 역시 계속 김현진 쪽에서 발견된다.
                """,
                "양지영",
                "현진이를 의심하는 건 쉬워요. 하지만 쉬운 결론일수록 마지막까지 다시 봐야 해요.",
                "bg_archive_room.png",
                "ch_yang_jiyeong_serious.png",
                List.of(
                        new Choice("회의록과 일정 변경 기록을 읽는다", "rooftop_note"),
                        new Choice("최종 추리를 한다", "final_board", g -> {}, g -> g.visitedScenes.contains("rooftop_note"))
                ),
                null
        ));

        scenes.put("rooftop_note", new Scene(
                "회의록과 일정 변경 기록",
                "Chapter 5  프로젝트 결론",
                """
                기록에는 같은 문장이 반복된다.
                '촬영 연기 제안'
                '안전 인원 부족'
                '장치 테스트 보류 필요'
                '공모전 마감 때문에 강행'
                김현진의 이름은 사건 현장마다 남아 있지만, 그 대부분은 범행의 흔적이 아니라 어떻게든 프로젝트를 멈춰 세우려던 실패한 시도였다.
                """,
                "플레이어",
                "의심의 재료로 보였던 기록이, 다시 읽자 경고의 기록으로 바뀐다.",
                "bg_archive_room.png",
                "ch_exorcist.png",
                List.of(new Choice("최종 추리를 한다", "final_board")),
                g -> g.unlockClue("프로젝트 회의록")
        ));

        scenes.put("final_board", new Scene(
                "최종 추리",
                "Final Board",
                """
                세 사고는 각각 다른 장소에서 일어났지만, 모두 같은 프로젝트의 압박과 안전 부재 속에서 이어졌다.
                학생들은 그 반복 속에서 김현진을 의심했고, 김현진의 침묵은 그 의심을 더 크게 만들었다.
                이제 플레이어는 최종 결론을 기록해야 한다.
                '김현진은 범인인가? 아니면 이 프로젝트의 실패가 진짜 원인인가?'
                """,
                "플레이어",
                "한 사람을 범인으로 만들면 이야기는 깔끔해진다. 하지만 깔끔한 결론이 항상 진실인 건 아니다.",
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
                "기록된 진실",
                "Ending  Truth",
                """
                플레이어는 김현진 개인에게 향한 의심을 걷어 내고, 세 사고를 하나의 구조적 실패로 기록한다.
                공모전 마감 압박, 반복된 강행 촬영, 비어 있던 안전 확인이 세 비극의 공통 원인이었다는 사실이 남는다.
                늦었지만 기록은 바로잡히고, 학교는 더 이상 이 사건들을 '설명하기 쉬운 범죄 이야기'로 포장하지 못한다.
                """,
                "양지영",
                "이제야 아이들이 왜 죽었는지 제대로 말할 수 있겠네요. 누가 미워서가 아니라, 모두가 멈추지 못해서였다고.",
                "bg_school_dawn.png",
                "ch_yang_jiyeong_confess.png",
                List.of(new Choice("시작 화면으로", "prologue_arrival", GameState::reset)),
                null
        ));

        scenes.put("ending_wrong_accusation", new Scene(
                "희생양",
                "Ending  Scapegoat",
                """
                플레이어는 김현진을 범인으로 기록한다.
                학교는 그 결론을 이용해 프로젝트의 안전 문제와 촬영 강행 책임을 한 사람의 악의로 덮어 버린다.
                사건은 해결된 것처럼 보이지만, 왜 세 사고가 반복되었는지는 끝내 바로잡히지 않는다.
                """,
                "플레이어",
                "가장 설명하기 쉬운 이름 하나를 남겼다. 대신 구조는 다시 어둠 속으로 밀려났다.",
                "bg_rooftop_night.png",
                "",
                List.of(new Choice("시작 화면으로", "prologue_arrival", GameState::reset)),
                null
        ));

        scenes.put("ending_silence", new Scene(
                "미완의 보고서",
                "Ending  Silence",
                """
                플레이어는 결론을 보류한다.
                학교는 사건들을 각자의 사고로 흩어 두고, 학생들은 여전히 김현진을 의심한 채 서로 다른 이야기를 믿는다.
                진실에 가장 가까이 갔지만 끝내 기록하지 못한 탓에, 프로젝트의 실패 역시 또렷한 책임으로 남지 못한다.
                """,
                "양지영",
                "말하지 않으면 아무것도 틀리지 않은 것처럼 보이죠. 하지만 그건 진실을 남기지 않는 방식일 뿐이에요.",
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
        boolean completed = isChoiceCompleted(choice);
        boolean enabled = choice.isVisible(state) && !completed;
        String suffix = completed ? " [완료]" : enabled ? "" : " [잠김]";
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
            case "pool_video", "pool_interview", "pool_review",
                    "music_record", "music_review",
                    "science_lab", "science_review",
                    "pool_facility_log", "pool_shadow_rumor", "pool_shoeprint",
                    "music_lid_trace", "music_corridor_witness", "music_torn_sheet",
                    "science_storage_photo", "science_cleanup_record", "science_handwritten_note",
                    "rooftop_gate", "rooftop_prejudice", "rooftop_note" -> state.visitedScenes.contains(choice.nextSceneId);
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

    private static boolean canReviewPool(GameState state) {
        return state.visitedScenes.contains("pool_video")
                && state.visitedScenes.contains("pool_interview");
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
