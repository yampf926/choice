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
    private static final int STORY_PANEL_HEIGHT = 196;
    private static final int HUD_SIDE_MARGIN = 18;
    private static final int HUD_BOTTOM_MARGIN = 14;
    private static final int SPEAKER_AREA_HEIGHT = 42;
    private static final int CONTINUE_AREA_HEIGHT = 24;
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

    // 화면은 배경 -> 캐릭터 -> 오버레이(텍스트/선택지) 순서로 겹쳐서 그린다.
    // titleLabel: 현재 장면 제목
    private final JLabel titleLabel = new JLabel("", SwingConstants.LEFT);
    // chapterLabel: 현재 장면의 챕터 표기
    private final JLabel chapterLabel = new JLabel("", SwingConstants.RIGHT);
    // backgroundLabel: 배경 이미지를 표시하는 레이어
    private final JLabel backgroundLabel = new JLabel("", SwingConstants.CENTER);
    // characterLabel: 캐릭터 이미지를 표시하는 레이어
    private final JLabel characterLabel = new JLabel("", SwingConstants.CENTER);
    // scenePanel: 장면의 모든 레이어를 올리는 루트 캔버스
    private final JPanel scenePanel = new JPanel(null);
    // overlayPanel: 어두운 그라데이션과 선택지 오버레이를 올리는 레이어
    private final JPanel overlayPanel = new JPanel(new BorderLayout());
    // hudPanel: 하단 대화 UI 전체를 고정 위치로 담는 패널
    private final JPanel hudPanel = new JPanel(null);
    // storyArea: 실제 대사/나레이션 글자가 출력되는 텍스트 영역
    private final JTextArea storyArea = new JTextArea();
    // continueLabel: NEXT, CHOICE 같은 진행 상태 문구
    private final JLabel continueLabel = new JLabel("CLICK", SwingConstants.RIGHT);
    // storyPanel: 텍스트칸 배경과 내부 텍스트를 묶는 하단 카드
    private final JPanel storyPanel = createStoryPanel();
    // speakerPanel: 화자 이름 배지를 담는 작은 패널
    private final JPanel speakerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    // speakerBadge: 현재 화자 이름을 표시하는 배지
    private final JLabel speakerBadge = new JLabel(" ", SwingConstants.LEFT);
    // choiceOverlay: 선택지 창을 화면 위에 띄우는 오버레이
    private final JPanel choiceOverlay = new JPanel(new GridBagLayout());
    // choicesPanel: 실제 선택지 버튼 목록이 들어가는 패널
    private final JPanel choicesPanel = new JPanel(new GridLayout(0, 1, 0, 10));
    // startOverlay: 시작 화면 카드가 올라가는 레이어
    private final JPanel startOverlay = new JPanel(new GridBagLayout());
    // galleryOverlay: 엔딩 모음 화면 카드가 올라가는 레이어
    private final JPanel galleryOverlay = new JPanel(new GridBagLayout());
    // nameField: 플레이어 이름 입력 칸
    private final JTextField nameField = new JTextField("");
    // galleryListPanel: 엔딩 목록 아이템을 세로로 쌓아두는 패널
    private final JPanel galleryListPanel = new JPanel();
    // imageCache: 이미 읽은 이미지를 메모리에 보관해 다시 로드하지 않게 함
    private final Map<String, BufferedImage> imageCache = new LinkedHashMap<>();
    // continueGameButton: 저장 데이터가 있을 때만 활성화되는 이어하기 버튼
    private JButton continueGameButton;

    // 모든 장면은 문자열 id로 관리한다. 선택지는 nextSceneId로 다음 장면을 가리킨다.
    private final Map<String, Scene> scenes = new LinkedHashMap<>();
    private final GameState state = new GameState();
    private final Map<String, String> endingTitles = new LinkedHashMap<>();
    private final Map<String, String> endingDescriptions = new LinkedHashMap<>();
    private final Set<String> narrationCharacterHiddenScenes = Set.of("prologue_principal");

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
        storyArea.setBackground(new Color(0, 0, 0, 0));
        storyArea.setOpaque(false);
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
        continueLabel.setOpaque(false);

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
        speakerPanel.setOpaque(false);
        speakerPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        speakerPanel.add(speakerBadge);
        scenePanel.add(speakerPanel);

        storyPanel.setOpaque(false);
        storyPanel.setLayout(null);
        storyPanel.setPreferredSize(new Dimension(0, STORY_PANEL_HEIGHT));
        storyPanel.setMinimumSize(new Dimension(0, STORY_PANEL_HEIGHT));
        storyPanel.add(storyArea);
        storyPanel.add(continueLabel);
        storyPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                advanceStory();
            }
        });

        hudPanel.setOpaque(false);
        hudPanel.add(storyPanel);

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
        scenePanel.add(hudPanel);
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
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int shadowInsetX = 6;
                int shadowInsetTop = 6;
                int shadowInsetBottom = 6;
                int cardWidth = Math.max(1, getWidth() - (shadowInsetX * 2));
                int shadowHeight = Math.max(1, getHeight() - shadowInsetTop - shadowInsetBottom);
                g2.setColor(new Color(0, 0, 0, 46));
                g2.fillRoundRect(shadowInsetX, shadowInsetTop, cardWidth, shadowHeight, 24, 24);
                g2.setColor(new Color(52, 58, 69, 236));
                g2.fillRoundRect(0, 0, Math.max(1, getWidth() - 1), Math.max(1, getHeight() - 1), 24, 24);
                g2.setColor(new Color(198, 206, 220, 70));
                g2.drawRoundRect(1, 1, Math.max(1, getWidth() - 3), Math.max(1, getHeight() - 3), 22, 22);
                g2.setColor(new Color(116, 125, 144, 150));
                int dividerY = Math.max(12, getHeight() - 18);
                g2.drawLine(20, dividerY, Math.max(20, getWidth() - 20), dividerY);
                g2.dispose();
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
        // 게임의 실제 스토리 데이터가 여기 들어 있다.
        // scenes.put("장면ID", new Scene(...)) 형식으로 장면을 추가하면 된다.
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
                "ch_player.png",
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
                "ch_kim_junyeong.png",
                List.of(new Choice("사건 정리로 간다", "case_hub")),
                null
        ));


        scenes.put("music_intro", new Scene(
                "음악실 사고",
                "Chapter 2  첫 번째 사고",
                """
                첫 번째 사고의 피해자는 음악부의 주다영이다.
                UCC 배경 음악을 다시 녹음하던 밤, 음악실에서 큰 충격음과 함께 사고가 일어났다.
                학생들은 복도에서 들린 발소리와 김세진의 동선을 엮어 곧바로 개입설을 만들었다.
                """,
                "양지영",
                "다영이는 실수를 만회하겠다며 늦게까지 남아 있었어요. 그래서 더더욱 누가 쫓아간 것처럼 보였죠.",
                "bg_music_room_night.png",
                "ch_ju_dayeong.png",
                List.of(
                        new Choice("녹음 파일과 현장 기록을 본다", "music_record"),
                        new Choice("복도 목격담을 확인한다", "music_corridor_witness"),
                        new Choice("찢어진 악보를 조사한다", "music_torn_sheet"),
                        new Choice("모은 단서를 정리한다", "music_review", g -> {}, Main::canReviewMusic)
                ),
                null
        ));

        scenes.put("music_record", new Scene(
                "녹음 파일과 체크리스트",
                "Chapter 2  첫 번째 사고",
                """
                녹음 파일에는 피아노 연습, 의자가 밀리는 소리, 잠깐의 정적, 그리고 뒤늦은 충격음이 순서대로 남아 있다.
                체크리스트에는 야간 단독 연습 금지와 장비 고정 확인이 적혀 있지만, 촬영 분량을 맞추기 위해 생략 표시가 덧붙어 있다.
                즉 사고는 누군가 숨어들기 전부터 이미 위험한 조건 위에 놓여 있었다.
                """,
                "플레이어",
                "소문보다 순서를 먼저 봐야 한다. 녹음은 누가 있었는지보다 무엇이 먼저 무너졌는지를 말해 준다.",
                "bg_music_room_close.png",
                "ch_ju_dayeong.png",
                List.of(
                        new Choice("복도 목격담을 확인한다", "music_corridor_witness"),
                        new Choice("찢어진 악보를 조사한다", "music_torn_sheet"),
                        new Choice("모은 단서를 정리한다", "music_review", g -> {}, Main::canReviewMusic)
                ),
                g -> g.unlockClue("음악실 녹음")
        ));

        scenes.put("music_corridor_witness", new Scene(
                "복도 목격담",
                "Chapter 2  첫 번째 사고",
                """
                복도에서 대기하던 학생들은 문이 한 번 세게 닫힌 뒤에도 몇 마디 연주가 더 이어졌다고 진술한다.
                즉 누군가가 즉시 들이닥쳐 공격했다기보다, 다영이 혼자 연습을 계속하던 시간이 분명히 있었다.
                김세진이 근처에 있었다는 말도 있지만 그 시점은 사고 직후 정리 요청을 받고 도착한 시간과 겹친다.
                """,
                "플레이어",
                "가까이 있었다는 사실만으로 원인을 고르면, 사고가 커진 과정이 통째로 비어 버린다.",
                "bg_music_room_night.png",
                "ch_ju_dayeong.png",
                List.of(
                        new Choice("녹음 파일과 현장 기록을 본다", "music_record"),
                        new Choice("찢어진 악보를 조사한다", "music_torn_sheet"),
                        new Choice("모은 단서를 정리한다", "music_review", g -> {}, Main::canReviewMusic)
                ),
                g -> g.unlockClue("복도 목격담")
        ));

        scenes.put("music_torn_sheet", new Scene(
                "찢어진 악보",
                "Chapter 2  첫 번째 사고",
                """
                넘어져 있던 악보대 아래에는 구겨진 악보와, 급히 수정한 박자 메모가 흩어져 있다.
                종이 가장자리에는 누군가와 몸싸움을 벌인 흔적보다는, 연습을 멈추지 못하고 계속 넘기다 찢긴 자국이 더 선명하다.
                다영은 실수를 만회하려고 한 곡을 더 녹음하려 했고, 그 조급함이 위험한 상태의 장비를 계속 쓰게 만들었다.
                """,
                "플레이어",
                "이건 위협의 흔적이라기보다, 멈추지 못한 연습의 흔적이다.",
                "bg_music_room_close.png",
                "ch_ju_dayeong.png",
                List.of(
                        new Choice("녹음 파일과 현장 기록을 본다", "music_record"),
                        new Choice("복도 목격담을 확인한다", "music_corridor_witness"),
                        new Choice("모은 단서를 정리한다", "music_review", g -> {}, Main::canReviewMusic)
                ),
                g -> g.unlockClue("찢어진 악보")
        ));

        scenes.put("music_review", new Scene(
                "음악실 단서 정리",
                "Chapter 2  첫 번째 사고",
                """
                모은 단서:
                음악실 녹음 / 복도 목격담 / 찢어진 악보

                녹음은 사고 직전까지 연습이 이어졌음을 보여 주고, 목격담은 즉각적인 습격보다 뒤늦은 소동을 가리킨다.
                악보는 야간 단독 연습과 점검 생략이 반복됐다는 사실을 남긴다.
                """,
                "플레이어",
                "이제는 누가 들어왔는지가 아니라, 다영이 왜 끝까지 음악실에 남아 있었는지를 다시 맞춰야 한다.",
                "bg_music_room_night.png",
                "ch_player.png",
                List.of(new Choice("사건을 재구성한다", "music_deduction")),
                null
        ));

        scenes.put("music_deduction", new Scene(
                "음악실 재구성 1",
                "Chapter 2  첫 번째 사고",
                """
                재구성 질문:
                다영이 위험한 상태의 음악실에 남아 있던 가장 큰 이유는 무엇인가.
                """,
                "플레이어",
                "쉽게는 누군가의 침입으로 설명할 수 있다. 하지만 그 선택으로는 반복된 점검 생략이 설명되지 않는다.",
                "bg_music_room_night.png",
                "ch_player.png",
                List.of(
                        new Choice("김세진이 숨어 있다가 다영이를 즉시 공격했다고 본다", "music_wrong", g -> { g.suspicionScore++; g.musicSolved = true; }, g -> true, true),
                        new Choice("촬영 분량을 맞추려는 조급함 때문에 다영이 위험한 연습을 계속했다고 본다", "music_reconstruction")
                ),
                null
        ));

        scenes.put("music_reconstruction", new Scene(
                "음악실 재구성 2",
                "Chapter 2  첫 번째 사고",
                """
                다음 질문:
                그렇다면 직접적인 사고는 어떤 순서로 벌어졌는가.
                """,
                "플레이어",
                "누군가와의 실랑이보다, 고정되지 않은 장비와 무리한 야간 연습이 겹친 쪽이 더 자연스럽다.",
                "bg_music_room_close.png",
                "ch_ju_dayeong.png",
                List.of(
                        new Choice("고정되지 않은 장비와 무리한 야간 연습이 겹쳐 사고가 났고, 뒤늦은 발소리가 괴담처럼 남았다", "music_true", g -> {
                            g.musicSolved = true;
                            g.truthScore++;
                            g.unlockClue("주다영 사건 해결");
                        }),
                        new Choice("다영이 누군가와 실랑이를 벌이다 즉시 가격당했다고 본다", "music_wrong", g -> { g.suspicionScore++; g.musicSolved = true; })
                ),
                null
        ));

        scenes.put("music_wrong", new Scene(
                "음악실 오판",
                "Chapter 2  첫 번째 사고",
                """
                김세진이 근처에 있었다는 사실은 강한 의심을 만든다.
                하지만 녹음의 순서와 악보 상태는 다영이 혼자 위험한 연습을 이어 갔다는 쪽을 더 강하게 지시한다.
                범인을 하나 고르는 데 성공해도, 왜 사고가 준비 과정에서 반복됐는지는 여전히 비어 있다.
                """,
                "플레이어",
                "쉬운 설명은 얻었지만, 사고가 왜 생겼는지는 놓쳤다.",
                "bg_music_room_night.png",
                "ch_player.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("music_true", new Scene(
                "음악실 사건 결론",
                "Chapter 2  첫 번째 사고",
                """
                주다영의 사고는 누군가의 습격보다, 야간 단독 연습과 점검 생략이 겹친 결과에 가깝다.
                사고 직후 복도 소리와 김세진의 동선은 강한 오해를 만들었지만, 직접 원인은 아니었다.
                첫 번째 사건은 이미 '멈추지 못한 촬영'이 어떻게 사람을 밀어 넣는지 보여 준다.
                """,
                "양지영",
                "그날 필요한 건 범인을 찾는 게 아니라, 연습을 멈추게 하는 일이었어요.",
                "bg_music_room_close.png",
                "ch_ju_dayeong.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("science_intro", new Scene(
                "과학실 사고",
                "Chapter 3  두 번째 사고",
                """
                두 번째 사고의 피해자는 과학부의 한승준이다.
                촬영용 실험 장면을 준비하던 중 과학실에서 폭발 사고가 일어났고, 학생들은 곧바로 누군가 약품을 바꿨다는 소문을 만들었다.
                특히 사고 직후 현장에 있던 김세진의 이름은 다시 의심의 중심으로 올라온다.
                """,
                "플레이어",
                "폭발은 조작처럼 보이기 쉽다. 그래서 더더욱 순서와 기록을 분리해 읽어야 한다.",
                "bg_science_lab_night.png",
                "ch_han_seungjun.png",
                List.of(
                        new Choice("장치 기록과 실험 메모를 조사한다", "science_lab"),
                        new Choice("정리 지시 기록을 확인한다", "science_cleanup_record"),
                        new Choice("손글씨 경고 메모를 조사한다", "science_handwritten_note"),
                        new Choice("모은 단서를 정리한다", "science_review", g -> {}, Main::canReviewScience)
                ),
                null
        ));

        scenes.put("science_lab", new Scene(
                "장치 기록과 실험 메모",
                "Chapter 3  두 번째 사고",
                """
                작업대 옆에는 반쯤 지워진 용기 라벨과 수정된 반응식 메모가 남아 있다.
                겉으로 보면 누군가가 일부러 장치를 건드린 것처럼 보이지만, 메모의 수정 방향은 위험을 키우려는 조작보다는 급히 수치를 맞추려는 보정에 가깝다.
                문제는 그 보정이 촬영을 맞추기 위해 안전 확인보다 먼저 실행됐다는 점이다.
                """,
                "플레이어",
                "의심할 만한 흔적은 선명하다. 하지만 성급한 수정 흔적이라는 해석도 충분히 가능하다.",
                "bg_science_explosion_mark.png",
                "ch_player.png",
                List.of(
                        new Choice("정리 지시 기록을 확인한다", "science_cleanup_record"),
                        new Choice("손글씨 경고 메모를 조사한다", "science_handwritten_note"),
                        new Choice("모은 단서를 정리한다", "science_review", g -> {}, Main::canReviewScience)
                ),
                g -> g.unlockClue("라벨 지워진 용기")
        ));

        scenes.put("science_cleanup_record", new Scene(
                "정리 지시 기록",
                "Chapter 3  두 번째 사고",
                """
                과학실 사용 기록에는 '촬영 배경 정리 우선', '실험은 오늘 안에 끝낼 것' 같은 지시가 반복된다.
                반면 보호 장비 확인과 환기 항목은 비어 있거나 뒤로 밀려 있다.
                누군가 몰래 약품을 바꾼 흔적보다, 모두가 위험을 알면서도 촬영 준비를 앞세운 흔적이 더 또렷하다.
                """,
                "플레이어",
                "정리 순서가 거꾸로 되어 있다. 그 무리수가 결국 장치보다 먼저 문제였다.",
                "bg_science_lab_night.png",
                "ch_player.png",
                List.of(
                        new Choice("장치 기록과 실험 메모를 조사한다", "science_lab"),
                        new Choice("손글씨 경고 메모를 조사한다", "science_handwritten_note"),
                        new Choice("모은 단서를 정리한다", "science_review", g -> {}, Main::canReviewScience)
                ),
                g -> g.unlockClue("정리 지시 기록")
        ));

        scenes.put("science_handwritten_note", new Scene(
                "손글씨 경고 메모",
                "Chapter 3  두 번째 사고",
                """
                작업대 모서리에는 '보호 안경 먼저', '환기 후 재가열' 같은 경고가 손글씨로 적혀 있다.
                그러나 바로 아래에는 '마감 전 촬영본 확보'가 더 굵은 글씨로 겹쳐 쓰여 있다.
                이 메모는 누군가의 범행 선언이 아니라, 경고가 매번 마감에 밀려난 현장의 분위기를 보여 준다.
                """,
                "플레이어",
                "문제는 누가 썼느냐보다, 왜 경고 문장이 늘 마지막에 지워졌느냐다.",
                "bg_science_explosion_mark.png",
                "ch_player.png",
                List.of(
                        new Choice("장치 기록과 실험 메모를 조사한다", "science_lab"),
                        new Choice("정리 지시 기록을 확인한다", "science_cleanup_record"),
                        new Choice("모은 단서를 정리한다", "science_review", g -> {}, Main::canReviewScience)
                ),
                g -> g.unlockClue("손글씨 경고 메모")
        ));

        scenes.put("science_review", new Scene(
                "과학실 단서 정리",
                "Chapter 3  두 번째 사고",
                """
                모은 단서:
                라벨 지워진 용기 / 정리 지시 기록 / 손글씨 경고 메모

                장치 흔적만 보면 조작처럼 보이지만, 기록을 겹쳐 보면 경고가 계속 밀리고 촬영 준비가 앞선다.
                과학실 사고는 누군가의 은밀한 개입보다, 위험한 상태를 알면서도 강행한 절차의 누적에 가깝다.
                """,
                "플레이어",
                "이제 승준이 어떤 환경 안에서 실험을 계속했는지를 순서대로 다시 세워야 한다.",
                "bg_science_lab_night.png",
                "ch_player.png",
                List.of(new Choice("사건을 재구성한다", "science_deduction")),
                null
        ));

        scenes.put("science_deduction", new Scene(
                "과학실 재구성 1",
                "Chapter 3  두 번째 사고",
                """
                재구성 질문:
                폭발 위험이 커진 첫 단계는 무엇인가.
                """,
                "플레이어",
                "조작이라고 단정하면 빠르다. 하지만 그러면 왜 보호 장비와 환기 절차가 비어 있었는지가 설명되지 않는다.",
                "bg_science_lab_night.png",
                "ch_player.png",
                List.of(
                        new Choice("김세진이 약품과 장치를 몰래 조작했다고 본다", "science_wrong", g -> { g.suspicionScore++; g.scienceSolved = true; }, g -> true, true),
                        new Choice("보호 장비 확인과 환기 절차가 촬영 준비 뒤로 밀리며 위험이 누적됐다고 본다", "science_reconstruction")
                ),
                null
        ));

        scenes.put("science_reconstruction", new Scene(
                "과학실 재구성 2",
                "Chapter 3  두 번째 사고",
                """
                다음 질문:
                그렇다면 마지막 폭발은 어떤 선택 끝에 일어났는가.
                """,
                "플레이어",
                "승준은 음모의 희생자라기보다, 멈추자는 요청이 묵살된 실험 안에 서 있었다.",
                "bg_science_explosion_mark.png",
                "ch_player.png",
                List.of(
                        new Choice("미완료 정리 상태에서 촬영용 실험을 강행해 반응이 커졌고, 그 결과 폭발이 일어났다", "science_true", g -> {
                            g.scienceSolved = true;
                            g.truthScore++;
                            g.unlockClue("한승준 사건 해결");
                        }),
                        new Choice("승준이 과장된 연출을 위해 혼자 위험한 약품을 바꿨다고 본다", "science_wrong", g -> { g.suspicionScore++; g.scienceSolved = true; })
                ),
                null
        ));

        scenes.put("science_wrong", new Scene(
                "과학실 오판",
                "Chapter 3  두 번째 사고",
                """
                장치 흔적만 보면 누군가 조작한 것처럼 보인다.
                그러나 정리 지시와 경고 메모는 현장이 이미 무리한 일정 속에 흔들리고 있었음을 보여 준다.
                조작이라는 결론은 선명하지만, 왜 위험이 반복됐는지는 설명하지 못한다.
                """,
                "플레이어",
                "가해자를 고르는 데는 성공했지만, 구조를 읽는 데는 실패했다.",
                "bg_science_explosion_mark.png",
                "ch_player.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("science_true", new Scene(
                "과학실 사건 결론",
                "Chapter 3  두 번째 사고",
                """
                한승준의 사고는 장치 조작보다, 촬영 마감을 위해 안전 절차를 계속 뒤로 미룬 결과에 가깝다.
                라벨 흔적과 수정 메모는 범행의 증거가 아니라, 무리한 일정 속에서 현장을 맞춰 가던 흔적이었다.
                두 번째 사건 역시 '멈추지 못한 프로젝트'가 어떻게 위험을 키우는지 보여 준다.
                """,
                "양지영",
                "다들 이상하다고는 했어요. 그런데 아무도 그날 당장 멈추게 하진 못했죠.",
                "bg_science_lab_night.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("pool_intro", new Scene(
                "수영장 사고",
                "Chapter 4  세 번째 사고",
                """
                세 번째 사고의 피해자는 수영부의 김준영이다.
                수중 촬영을 준비하던 밤, 구조 타이밍이 몇 초 어긋나며 익사 사고가 발생했다.
                준영이 평소 수영을 잘했다는 사실 때문에 학생들은 오히려 누군가 의도적으로 방해했다고 믿기 시작한다.
                """,
                "양지영",
                "준영이는 체력이 좋았어요. 그래서 애들이 다들 더더욱 사고라는 말을 못 믿었죠.",
                "bg_pool_night.png",
                "ch_kim_junyeong.png",
                List.of(
                        new Choice("수중 촬영 영상과 일정표를 본다", "pool_video"),
                        new Choice("현장 지원 기록을 확인한다", "pool_interview"),
                        new Choice("시설 점검표와 안전 장비를 확인한다", "pool_facility_log"),
                        new Choice("모은 단서를 정리한다", "pool_review", g -> {}, Main::canReviewPool)
                ),
                null
        ));

        scenes.put("pool_video", new Scene(
                "수중 촬영 영상",
                "Chapter 4  세 번째 사고",
                """
                영상에는 구조 인력이 바로 뛰어들지 못한 몇 초의 공백이 남아 있다.
                카메라 위치는 촬영 구도를 우선한 방향으로 바뀌어 있고, 안전 로프가 프레임 밖으로 치워진 흔적도 보인다.
                학생들은 이 변화를 김세진의 배치 조작으로 해석하지만, 영상만으로는 의도를 단정할 수 없다.
                """,
                "플레이어",
                "문제는 누가 손댔느냐보다, 왜 구조보다 화면 구도가 먼저였느냐는 쪽에 가깝다.",
                "bg_pool_edge.png",
                "ch_kim_junyeong.png",
                List.of(
                        new Choice("현장 지원 기록을 확인한다", "pool_interview"),
                        new Choice("시설 점검표와 안전 장비를 확인한다", "pool_facility_log"),
                        new Choice("모은 단서를 정리한다", "pool_review", g -> {}, Main::canReviewPool)
                ),
                g -> g.unlockClue("수영장 영상")
        ));

        scenes.put("pool_interview", new Scene(
                "현장 지원 기록",
                "Chapter 4  세 번째 사고",
                """
                보조 인력 배치표에는 원래 네 명이던 안전 인원이 두 명으로 줄어든 기록이 남아 있다.
                김세진은 촬영을 미루자고 적어 두었지만, 공모전 마감과 인원 부족 때문에 결국 강행됐다는 메모가 이어진다.
                사고 직후 학생들이 본 것은 누군가를 끌어올리는 장면이 아니라, 이미 늦어진 구조였다.
                """,
                "플레이어",
                "세 번째 사고에서도 먼저 보이는 건 개인의 흔적보다 무너진 통제다.",
                "bg_pool_night.png",
                "ch_kim_junyeong.png",
                List.of(
                        new Choice("수중 촬영 영상과 일정표를 본다", "pool_video"),
                        new Choice("시설 점검표와 안전 장비를 확인한다", "pool_facility_log"),
                        new Choice("모은 단서를 정리한다", "pool_review", g -> {}, Main::canReviewPool)
                ),
                g -> g.unlockClue("현장 지원 기록")
        ));

        scenes.put("pool_facility_log", new Scene(
                "시설 점검표와 안전 장비",
                "Chapter 4  세 번째 사고",
                """
                수영장 점검표에는 촬영 구간의 수심 표시 부표가 전날 분리되었다는 기록이 남아 있다.
                대체 장비 신청은 올라갔지만 '공모전 촬영 우선' 메모와 함께 보류되었고, 안전 로프도 화면을 가린다는 이유로 치워져 있다.
                즉 현장은 누군가의 함정보다, 위험을 알면서도 나중으로 미뤄 둔 안전 조치의 빈자리로 보인다.
                """,
                "플레이어",
                "로프가 없는 이유가 범행 준비인지, 촬영 강행의 부산물인지가 핵심이다.",
                "bg_pool_edge.png",
                "ch_ju_dayeong.png",
                List.of(
                        new Choice("수중 촬영 영상과 일정표를 본다", "pool_video"),
                        new Choice("현장 지원 기록을 확인한다", "pool_interview"),
                        new Choice("모은 단서를 정리한다", "pool_review", g -> {}, Main::canReviewPool)
                ),
                g -> g.unlockClue("수영장 시설 점검표")
        ));

        scenes.put("pool_review", new Scene(
                "수영장 단서 정리",
                "Chapter 4  세 번째 사고",
                """
                모은 단서:
                수영장 영상 / 현장 지원 기록 / 수영장 시설 점검표

                영상에는 구조 공백이 남아 있고, 지원 기록에는 안전 인력 축소가 적혀 있다.
                점검표는 수심 표시와 안전 로프가 조작이 아니라 촬영 우선 판단 때문에 사라졌음을 보여 준다.
                """,
                "플레이어",
                "이제 준영의 마지막 몇 초를 다시 세워야 한다. 무엇이 먼저 무너졌는지가 결론을 바꾼다.",
                "bg_pool_night.png",
                "ch_ju_dayeong.png",
                List.of(new Choice("사건을 재구성한다", "pool_deduction")),
                null
        ));

        scenes.put("pool_deduction", new Scene(
                "수영장 재구성 1",
                "Chapter 4  세 번째 사고",
                """
                재구성 질문:
                가장 먼저 무너진 것은 무엇인가.
                """,
                "플레이어",
                "누군가가 밀어 넣었다고 고르면 쉽다. 하지만 그러면 왜 안전선이 비어 있었는지가 남는다.",
                "bg_pool_night.png",
                "ch_player.png",
                List.of(
                        new Choice("김세진이 일부러 배치를 바꿔 준영을 위험하게 만들었다고 본다", "pool_wrong", g -> { g.suspicionScore++; g.poolSolved = true; }, g -> true, true),
                        new Choice("안전 로프 철거와 보조 인력 축소가 먼저 일어나 구조선이 무너졌다고 본다", "pool_reconstruction")
                ),
                null
        ));

        scenes.put("pool_reconstruction", new Scene(
                "수영장 재구성 2",
                "Chapter 4  세 번째 사고",
                """
                다음 질문:
                그렇다면 직접적인 비극은 어떤 공백에서 커졌는가.
                """,
                "플레이어",
                "준영은 갑자기 약해진 게 아니다. 위험 신호를 봐 줄 사람이 제때 닿지 못한 쪽이 더 자연스럽다.",
                "bg_pool_edge.png",
                "ch_kim_junyeong.png",
                List.of(
                        new Choice("촬영 구도를 맞추느라 구조 인력이 분산된 몇 초의 공백이 사고를 키웠다", "pool_true", g -> {
                            g.poolSolved = true;
                            g.truthScore++;
                            g.unlockClue("김준영 사건 해결");
                        }),
                        new Choice("준영이 혼자 무리하게 잠수했다가 우연히 아무도 못 본 틈에 사고가 났다", "pool_wrong", g -> { g.suspicionScore++; g.poolSolved = true; })
                ),
                null
        ));

        scenes.put("pool_wrong", new Scene(
                "수영장 오판",
                "Chapter 4  세 번째 사고",
                """
                김세진의 배치 변경 흔적은 강한 의심을 만든다.
                그러나 지원 기록과 점검표는 그 변화가 범행 설계라기보다, 무너진 현장을 억지로 맞추려던 흔적에 더 가깝다고 말한다.
                범인을 하나 정하는 데 성공해도, 왜 사고가 가능했는지는 여전히 남는다.
                """,
                "플레이어",
                "의심은 남았지만 구조는 읽지 못했다.",
                "bg_pool_edge.png",
                "ch_player.png",
                List.of(new Choice("사건 정리로 돌아간다", "case_hub")),
                null
        ));

        scenes.put("pool_true", new Scene(
                "수영장 사건 결론",
                "Chapter 4  세 번째 사고",
                """
                김준영의 사고는 개인의 악의보다, 촬영 강행과 안전 인력 부족이 겹쳐 만든 구조적 사고에 가깝다.
                수영을 잘하던 준영이었기에 더더욱 누군가의 의도를 상상하기 쉬웠지만, 실제로 무너진 것은 구조선과 판단 순서였다.
                세 번째 사건은 세 사고의 공통 원인을 가장 선명하게 드러낸다.
                """,
                "양지영",
                "이제야 다들 왜 그날 구조보다 촬영이 먼저였는지 보이네요.",
                "bg_pool_edge.png",
                "ch_kim_junyeong.png",
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
                "ch_player.png",
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
                "Chapter 5  프로젝트 결말",
                """
                세 사건을 모두 정리하고 나면 마지막 질문이 남는다.
                김세진은 왜 모든 현장 가까이에 있었고, 왜 늘 의심의 중심에 섰는가.
                그 답을 적으려면 마지막으로 세진의 동선과, 학생들이 그를 범인처럼 믿게 된 과정을 다시 읽어야 한다.
                """,
                "양지영",
                "세진이를 의심하는 건 쉬워요. 하지만 쉬운 결론일수록 마지막까지 다시 봐야 해요.",
                "bg_archive_room.png",
                "ch_yang_jiyeong_soft.png",
                List.of(
                        new Choice("옥상 출입 기록을 확인한다", "rooftop_gate"),
                        new Choice("학생들 사이에 퍼진 의심을 듣는다", "rooftop_prejudice"),
                        new Choice("회의록과 일정 변경 기록을 읽는다", "rooftop_note"),
                        new Choice("최종 재구성을 시작한다", "final_board", g -> {}, Main::canReviewFinalBoard)
                ),
                null
        ));

        scenes.put("rooftop_gate", new Scene(
                "옥상 출입 기록",
                "Chapter 5  프로젝트 결말",
                """
                출입 기록에는 사고가 벌어진 날마다 김세진의 이름이 남아 있다.
                하지만 같은 줄마다 '촬영 중단 요청', '안전 점검 문의', '대체 인원 필요' 같은 메모도 함께 적혀 있다.
                세진의 이동은 사건을 설계한 동선이라기보다, 반복해서 문제를 수습하러 뛰어다닌 동선에 더 가깝다.
                """,
                "플레이어",
                "이름만 떼어 보면 범인 같고, 메모까지 읽으면 가장 먼저 경고한 사람처럼 보인다.",
                "bg_rooftop_edge.png",
                "ch_player.png",
                List.of(
                        new Choice("학생들 사이에 퍼진 의심을 듣는다", "rooftop_prejudice"),
                        new Choice("회의록과 일정 변경 기록을 읽는다", "rooftop_note"),
                        new Choice("최종 재구성을 시작한다", "final_board", g -> {}, Main::canReviewFinalBoard)
                ),
                g -> g.unlockClue("옥상 출입 기록")
        ));

        scenes.put("rooftop_prejudice", new Scene(
                "퍼져 버린 의심",
                "Chapter 5  프로젝트 결말",
                """
                학생들의 말은 놀랄 만큼 비슷하다.
                '문제가 생기면 항상 세진 선배가 먼저 왔어.'
                '그러니까 더 수상했어.'
                그러나 되짚어 보면 그 인상은 사건의 원인보다, 모두가 설명하기 쉬운 얼굴 하나를 찾으려는 심리에서 커졌다.
                """,
                "플레이어",
                "오해를 진실처럼 보이게 만드는 구조는 늘 같다. 자주 보인 사람은 범인이 되고, 자주 무시된 규칙은 배경이 된다.",
                "bg_rooftop_night.png",
                "ch_player.png",
                List.of(
                        new Choice("옥상 출입 기록을 확인한다", "rooftop_gate"),
                        new Choice("회의록과 일정 변경 기록을 읽는다", "rooftop_note"),
                        new Choice("최종 재구성을 시작한다", "final_board", g -> {}, Main::canReviewFinalBoard)
                ),
                g -> g.unlockClue("의심이 퍼진 증언")
        ));

        scenes.put("rooftop_note", new Scene(
                "회의록과 일정 변경 기록",
                "Chapter 5  프로젝트 결말",
                """
                기록에는 같은 문장이 반복된다.
                '촬영 연기 제안'
                '안전 인원 부족'
                '실험 대기 필요'
                '공모전 마감으로 강행'
                김세진의 이름은 현장마다 남아 있지만, 대부분은 문제를 보고하고도 프로젝트를 멈추지 못한 흔적이다.
                """,
                "플레이어",
                "의심의 재료처럼 보이던 기록이 다시 읽자 경고 기록으로 뒤집힌다.",
                "bg_archive_room.png",
                "ch_player.png",
                List.of(
                        new Choice("옥상 출입 기록을 확인한다", "rooftop_gate"),
                        new Choice("학생들 사이에 퍼진 의심을 듣는다", "rooftop_prejudice"),
                        new Choice("최종 재구성을 시작한다", "final_board", g -> {}, Main::canReviewFinalBoard)
                ),
                g -> g.unlockClue("프로젝트 회의록")
        ));

        scenes.put("final_board", new Scene(
                "최종 재구성",
                "Final Board",
                """
                최종 단서:
                옥상 출입 기록 / 의심이 퍼진 증언 / 프로젝트 회의록

                세 기록을 겹쳐 보면, 김세진은 사건을 설계한 사람이라기보다 반복해서 위험을 경고했지만 끝내 멈추게 하지는 못한 사람에 가깝다.
                학생들은 현장마다 보인 세진을 범인으로 묶었고, 그 사이 공모전 마감과 촬영 강행, 안전 점검 누락은 진짜 원인인데도 배경으로 밀렸다.
                먼저 세 사건을 하나의 문장으로 재구성해야 한다.
                """,
                "플레이어",
                "누가 미웠는지가 아니라, 무엇이 반복됐는지를 먼저 기록해야 한다.",
                "bg_chapel_night.png",
                "ch_player.png",
                List.of(
                        new Choice("세 사건은 모두 누군가의 직접적 살해이며 김세진이 그 중심에 있었다고 본다", "ending_wrong_accusation", g -> g.finalChoice = "accuse"),
                        new Choice("세 사건은 공모전 마감 압박과 촬영 강행이 만든 연쇄 사고였다고 재구성한다", "final_verdict"),
                        new Choice("끝내 하나의 구조로 묶지 못하겠다", "ending_silence", g -> g.finalChoice = "silence")
                ),
                null
        ));

        scenes.put("final_verdict", new Scene(
                "최종 결론 기록",
                "Final Verdict",
                """
                재구성은 끝났다.
                이제 마지막으로 공식 기록의 문장을 정해야 한다.
                """,
                "플레이어",
                "세진의 침묵과 동선은 의심의 재료였지만, 사건의 원인 자체는 아니었다. 무엇을 남길 것인가.",
                "bg_school_dawn.png",
                "ch_player.png",
                List.of(
                        new Choice("김세진은 범인이 아니다", "ending_true", g -> g.finalChoice = "innocent"),
                        new Choice("그래도 김세진을 범인으로 기록한다", "ending_wrong_accusation", g -> g.finalChoice = "accuse"),
                        new Choice("보고를 끝내지 못한다", "ending_silence", g -> g.finalChoice = "silence")
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
        speakerPanel.setVisible(false);
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
        speakerPanel.setVisible(false);
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
        // 장면 진입 시 제목/이미지/방문 기록을 갱신하고,
        // 아래 prepareSceneFlow()에서 텍스트 페이지와 선택지를 준비한다.
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
        String label = applyPlayerName(choice.label);
        JButton button = new JButton(asWrappedHtml(label, 36));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setFocusPainted(false);
        button.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
        button.setPreferredSize(new Dimension(320, 56));
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
            performChoice(choice);
        });
        return button;
    }

    private boolean isChoiceCompleted(Choice choice) {
        return switch (choice.nextSceneId) {
            case "pool_intro" -> state.poolSolved;
            case "music_intro" -> state.musicSolved;
            case "science_intro" -> state.scienceSolved;
            case "pool_video", "pool_interview", "pool_facility_log", "pool_review",
                    "music_record", "music_corridor_witness", "music_torn_sheet", "music_review",
                    "science_lab", "science_cleanup_record", "science_handwritten_note", "science_review",
                    "rooftop_gate", "rooftop_prejudice", "rooftop_note",
                    "pool_reconstruction", "music_reconstruction", "science_reconstruction", "final_verdict" ->
                    state.visitedScenes.contains(choice.nextSceneId);
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

    private static boolean hasVisitedAll(GameState state, String... ids) {
        for (String id : ids) {
            if (!state.visitedScenes.contains(id)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canReviewPool(GameState state) {
        return hasVisitedAll(state, "pool_video", "pool_interview", "pool_facility_log");
    }

    private static boolean canReviewMusic(GameState state) {
        return hasVisitedAll(state, "music_record", "music_corridor_witness", "music_torn_sheet");
    }

    private static boolean canReviewScience(GameState state) {
        return hasVisitedAll(state, "science_lab", "science_cleanup_record", "science_handwritten_note");
    }

    private static boolean canReviewFinalBoard(GameState state) {
        return hasVisitedAll(state, "rooftop_gate", "rooftop_prejudice", "rooftop_note");
    }

    private void startDialogueAnimation(String fullText) {
        // 타이핑 효과는 Timer로 한 글자씩 storyArea에 추가하는 방식이다.
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
        int storyWidth = Math.max(1, width - (HUD_SIDE_MARGIN * 2));
        int hudHeight = STORY_PANEL_HEIGHT + HUD_BOTTOM_MARGIN;
        int hudY = Math.max(0, height - hudHeight);
        hudPanel.setBounds(0, hudY, width, hudHeight);
        storyPanel.setBounds(HUD_SIDE_MARGIN, 0, storyWidth, STORY_PANEL_HEIGHT);
        int speakerWidth = Math.min(Math.max(120, speakerBadge.getPreferredSize().width), Math.max(120, storyWidth - 28));
        int speakerX = HUD_SIDE_MARGIN + 14;
        int speakerY = Math.max(12, hudY - SPEAKER_AREA_HEIGHT + 10);
        speakerPanel.setBounds(speakerX, speakerY, speakerWidth, SPEAKER_AREA_HEIGHT);
        int textAreaY = 10;
        int textAreaHeight = Math.max(1, STORY_PANEL_HEIGHT - textAreaY - CONTINUE_AREA_HEIGHT);
        storyArea.setBounds(0, textAreaY, storyWidth, textAreaHeight);
        continueLabel.setBounds(0, STORY_PANEL_HEIGHT - CONTINUE_AREA_HEIGHT, storyWidth - 12, CONTINUE_AREA_HEIGHT);
        startOverlay.setBounds(0, 0, width, height);
        galleryOverlay.setBounds(0, 0, width, height);
        int topInset = speakerPanel.isVisible() ? Math.max(12, speakerY + SPEAKER_AREA_HEIGHT + 8) : 12;
        choiceOverlay.setBorder(new EmptyBorder(topInset, HUD_SIDE_MARGIN, hudHeight + 8, HUD_SIDE_MARGIN));
        scenePanel.setComponentZOrder(backgroundLabel, 6);
        scenePanel.setComponentZOrder(characterLabel, 5);
        scenePanel.setComponentZOrder(overlayPanel, 4);
        scenePanel.setComponentZOrder(hudPanel, 3);
        scenePanel.setComponentZOrder(speakerPanel, 2);
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
        // 현재 장면에서 실제로 보여줄 선택지와 텍스트 페이지를 계산한다.
        visibleChoices = collectVisibleChoices(scene);
        scenePages = buildPages(currentSceneId, scene);
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

    private List<PageEntry> buildPages(String sceneId, Scene scene) {
        // 한 장면의 텍스트를 "한 번에 보여줄 페이지" 목록으로 자른다.
        // 내레이션과 대사는 같은 장면 안에 있어도 서로 다른 페이지가 된다.
        List<PageEntry> pages = new ArrayList<>();
        String narrationCharacterImage = narrationCharacterHiddenScenes.contains(sceneId) ? "" : scene.characterImage;
        if (scene.narration != null && !scene.narration.isBlank()) {
            pages.addAll(splitIntoPages(scene.narration, "", scene.backgroundImage, narrationCharacterImage));
        }
        if (scene.dialogue != null && !scene.dialogue.isBlank()) {
            pages.addAll(splitIntoPages(scene.dialogue, scene.speaker, scene.backgroundImage, scene.characterImage));
        }
        return pages;
    }

    private List<PageEntry> splitIntoPages(String text, String speaker, String backgroundImage, String characterImage) {
        // 문장을 1~2개씩 묶어서 한 페이지를 만든다.
        // 너무 긴 문장은 MAX_PAGE_CHARS 기준으로 다음 페이지로 넘긴다.
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
                pages.add(new PageEntry(page.toString(), speaker == null ? "" : speaker.strip(), backgroundImage, characterImage));
                page.setLength(0);
                sentenceCount = 0;
            }
            if (page.length() > 0) {
                page.append(' ');
            }
            page.append(sentence);
            sentenceCount++;
            if (sentenceCount == 2 && page.length() >= MAX_PAGE_CHARS / 2) {
                pages.add(new PageEntry(page.toString(), speaker == null ? "" : speaker.strip(), backgroundImage, characterImage));
                page.setLength(0);
                sentenceCount = 0;
            }
        }

        if (page.length() > 0) {
            pages.add(new PageEntry(page.toString(), speaker == null ? "" : speaker.strip(), backgroundImage, characterImage));
        }
        return pages;
    }

    private String applyPlayerName(String text) {
        String name = state.playerName == null || state.playerName.isBlank() ? DEFAULT_PLAYER_NAME : state.playerName.strip();
        return text.replace("플레이어", name);
    }

    private void showCurrentPage() {
        currentPage = scenePages.get(currentPageIndex);
        activeBackgroundImage = currentPage.backgroundImage();
        activeCharacterImage = currentPage.characterImage();
        refreshImages();
        updateSpeakerBadge();
        layoutSceneLayers();
        continueLabel.setText(pageFullyVisible ? nextPromptText() : "...");
        startDialogueAnimation(currentPage.text());
    }

    private void advanceStory() {
        // 클릭 시 동작 순서:
        // 1) 아직 타이핑 중이면 즉시 전체 문장 표시
        // 2) 다음 페이지가 있으면 다음 페이지로 이동
        // 3) 마지막 페이지면 선택지 또는 엔딩 처리
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
        if (visibleChoices.size() == 1) {
            performChoice(visibleChoices.get(0));
            return;
        }
        showChoicesOverlay();
    }

    private void performChoice(Choice choice) {
        choice.effect.accept(state);
        if (choice.recordsSuspicion) {
            state.suspectHistory.add(choice.label);
        }
        persistState();
        showScene(choice.nextSceneId);
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
        return "<html><div style='width:" + widthEm + "em; text-align:center;'>" + safe + "</div></html>";
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
        // Scene은 "한 장면에 필요한 데이터 묶음"이다.
        // 텍스트, 화자, 이미지, 선택지, 진입 시 실행할 로직을 함께 가진다.
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
        // Choice는 버튼 하나의 데이터다.
        // label: 버튼 글자, nextSceneId: 이동할 장면, effect: 상태값 변경 로직
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

    private record PageEntry(String text, String speaker, String backgroundImage, String characterImage) {
        // 최종 렌더링 단위. 실제 화면에는 Scene이 아니라 PageEntry가 하나씩 출력된다.
        private static PageEntry empty() {
            return new PageEntry("", "", "", "");
        }
    }

    private static class SaveData {
        // SaveData는 GameState를 파일에 저장하기 쉬운 형태로 옮긴 객체다.
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
