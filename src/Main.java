import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Main extends JFrame {
    private static final double MOBILE_ASPECT_RATIO = 9.0 / 16.0;
    private static final Color NIGHT = new Color(6, 8, 14);
    private static final Color PANEL = new Color(8, 12, 20, 222);
    private static final Color PANEL_SOFT = new Color(16, 22, 35, 214);
    private static final Color ACCENT = new Color(146, 206, 255);
    private static final Color ACCENT_SOFT = new Color(82, 116, 153);
    private static final Color BUTTON = new Color(15, 22, 34);
    private static final Color BUTTON_HOVER = new Color(31, 45, 68);

    private final JLabel titleLabel = new JLabel("", SwingConstants.LEFT);
    private final JLabel chapterLabel = new JLabel("", SwingConstants.RIGHT);
    private final JLabel backgroundLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel characterLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel speakerLabel = new JLabel("");
    private final JTextArea narrationArea = new JTextArea();
    private final JTextArea dialogueArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("");
    private final JPanel scenePanel = new JPanel(null);
    private final JPanel overlayPanel = new JPanel(new BorderLayout());
    private final JPanel hudPanel = new JPanel(new BorderLayout(0, 10));
    private final JPanel choicesPanel = new JPanel(new GridLayout(0, 1, 0, 8));

    private final Map<String, Scene> scenes = new LinkedHashMap<>();
    private final GameState state = new GameState();

    private Scene currentScene;
    private Timer dialogueTimer;
    private boolean adjustingFrame;

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

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        titleLabel.setForeground(new Color(240, 244, 250));
        titleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        titleLabel.setBorder(new EmptyBorder(4, 8, 8, 8));
        chapterLabel.setForeground(ACCENT);
        chapterLabel.setFont(new Font("Monospaced", Font.PLAIN, 13));
        chapterLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(chapterLabel, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        scenePanel.setOpaque(false);
        root.add(scenePanel, BorderLayout.CENTER);

        backgroundLabel.setOpaque(true);
        backgroundLabel.setBackground(new Color(12, 16, 24));
        backgroundLabel.setForeground(new Color(175, 186, 202));
        backgroundLabel.setFont(new Font("Serif", Font.PLAIN, 18));
        backgroundLabel.setHorizontalAlignment(SwingConstants.CENTER);
        backgroundLabel.setVerticalAlignment(SwingConstants.CENTER);
        scenePanel.add(backgroundLabel);

        characterLabel.setOpaque(false);
        characterLabel.setHorizontalAlignment(SwingConstants.CENTER);
        characterLabel.setVerticalAlignment(SwingConstants.BOTTOM);
        characterLabel.setForeground(new Color(240, 244, 250));
        characterLabel.setFont(new Font("Serif", Font.BOLD, 16));
        scenePanel.add(characterLabel);

        overlayPanel.setOpaque(false);
        scenePanel.add(overlayPanel);

        narrationArea.setEditable(false);
        narrationArea.setLineWrap(true);
        narrationArea.setWrapStyleWord(true);
        narrationArea.setFont(new Font("Serif", Font.PLAIN, 15));
        narrationArea.setForeground(new Color(225, 230, 238));
        narrationArea.setBackground(PANEL_SOFT);
        narrationArea.setBorder(new EmptyBorder(12, 14, 12, 14));

        speakerLabel.setFont(new Font("Serif", Font.BOLD, 18));
        speakerLabel.setForeground(new Color(241, 246, 255));
        speakerLabel.setOpaque(true);
        speakerLabel.setBackground(new Color(9, 18, 30, 234));
        speakerLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_SOFT, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        dialogueArea.setEditable(false);
        dialogueArea.setLineWrap(true);
        dialogueArea.setWrapStyleWord(true);
        dialogueArea.setFont(new Font("Serif", Font.PLAIN, 17));
        dialogueArea.setForeground(new Color(236, 239, 246));
        dialogueArea.setBackground(PANEL);
        dialogueArea.setBorder(new EmptyBorder(12, 14, 14, 14));

        JPanel textBlock = new JPanel(new BorderLayout(0, 8));
        textBlock.setOpaque(false);
        textBlock.setBorder(new EmptyBorder(0, 0, 10, 0));
        textBlock.add(wrapPanel("상황", narrationArea, PANEL_SOFT), BorderLayout.NORTH);
        textBlock.add(wrapDialogue(), BorderLayout.CENTER);

        hudPanel.setOpaque(false);
        hudPanel.setBorder(new EmptyBorder(120, 14, 12, 14));
        hudPanel.add(textBlock, BorderLayout.NORTH);

        statusLabel.setForeground(ACCENT);
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 13));
        statusLabel.setBorder(new EmptyBorder(0, 4, 4, 0));
        hudPanel.add(statusLabel, BorderLayout.CENTER);

        choicesPanel.setOpaque(false);
        hudPanel.add(choicesPanel, BorderLayout.SOUTH);

        overlayPanel.add(createBottomShade(), BorderLayout.CENTER);
        overlayPanel.add(hudPanel, BorderLayout.SOUTH);

        initScenes();
        resetState();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                enforceMobileAspectRatio();
                layoutSceneLayers();
                refreshImages();
            }
        });

        showScene("prologue_arrival");
        SwingUtilities.invokeLater(() -> {
            layoutSceneLayers();
            refreshImages();
            scenePanel.repaint();
        });
    }

    private JPanel wrapDialogue() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(speakerLabel, BorderLayout.NORTH);
        panel.add(wrapPanel("", dialogueArea, PANEL), BorderLayout.CENTER);
        return panel;
    }

    private JPanel wrapPanel(String name, JTextArea area, Color color) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(color);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ACCENT_SOFT, 1),
                        BorderFactory.createLineBorder(new Color(255, 255, 255, 18), 1)
                ),
                new EmptyBorder(3, 3, 3, 3)
        ));
        if (!name.isEmpty()) {
            JLabel label = new JLabel(name);
            label.setFont(new Font("Serif", Font.BOLD, 13));
            label.setForeground(ACCENT);
            label.setBorder(new EmptyBorder(4, 8, 4, 0));
            panel.add(label, BorderLayout.NORTH);
        }
        panel.add(area, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createBottomShade() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                GradientPaint paint = new GradientPaint(0, 0, new Color(3, 6, 10, 0), 0, getHeight(), new Color(2, 4, 9, 210));
                g2.setPaint(paint);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
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
                "월야학원에 와주셔서 감사합니다. 학교를 뒤흔드는 소문을 조용히 정리해 주십시오.",
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
                "세 건... 아니요, 마지막 한 건까지 합치면 네 건이에요. 다들 사고라고 했지만 아무도 마음속으로는 그렇게 믿지 않았어요.",
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
                List.of(new Choice("수영장으로 간다", "pool_intro")),
                null
        ));

        scenes.put("pool_intro", new Scene(
                "수영장 사고",
                "Chapter 2  수영장 사건",
                """
                피해자: 염선혜. 사진 동아리 학생.
                학생들은 '수영장 CCTV 공백과 문 앞 그림자' 때문에 누군가 선혜를 물에 밀어 넣었다고 믿는다.
                하지만 현장은 살인과 사고 어느 쪽으로도 읽히는 단서들이 뒤섞여 있다.
                """,
                "양지영",
                "선혜는 수중 촬영 대회를 준비하고 있었어요. 그런데 애가 죽은 밤, 영상에는 사람 그림자가 찍혔어요.",
                "bg_pool_night.png",
                "ch_yang_jiyeong.png",
                List.of(
                        new Choice("선혜의 카메라 영상을 본다", "pool_video"),
                        new Choice("사건 허브로 돌아간다", "case_hub")
                ),
                null
        ));

        scenes.put("pool_video", new Scene(
                "카메라 영상",
                "Chapter 2  수영장 사건",
                """
                영상 마지막 프레임에는 수영장 문 근처를 스쳐 지나가는 그림자가 남아 있다.
                물은 이상할 정도로 크게 흔들렸고, 바닥 타일에는 미끄러진 듯한 긁힘이 남아 있다.
                그림자는 살인의 증거처럼 보이지만, 청소 장치가 자동 가동된 기록도 같은 시각에 남아 있다.
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
                "누군가를 의심하게 만드는 요소는 많다. 하지만 하나씩 뜯어보면 전부 다른 설명이 가능하다.",
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
                        new Choice("타살로 본다", "pool_wrong", g -> g.suspicionScore++),
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
                "물... 사람이 아니라 물이 먼저였어. 난 무언가를 피한 게 아니라, 흔들린 거야.",
                "bg_pool_edge.png",
                "ch_ghost_yeom_seonhye_soft.png",
                List.of(new Choice("다음 사건으로 간다", "music_intro")),
                null
        ));

        scenes.put("pool_true", new Scene(
                "수영장 사건 결론",
                "Chapter 2  수영장 사건",
                """
                진실:
                염선혜는 밤 수중 촬영 도중 자동 청소 장치가 가동되며 크게 흔들린 물살에 균형을 잃었다.
                젖은 타일에서 미끄러져 머리를 부딪힌 뒤 익사했다.
                그림자는 청소 직원의 순찰이 남긴 우연한 흔적이었다.
                """,
                "양지영",
                "살인이 아니라 사고였다는 사실이 더 허무하게 느껴지죠. 그런데도 다들 더 무서운 이야기를 믿고 싶어 했어요.",
                "bg_pool_edge.png",
                "ch_yang_jiyeong.png",
                List.of(new Choice("음악실 사건으로 간다", "music_intro")),
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
                        new Choice("녹음 파일을 분석한다", "music_record"),
                        new Choice("사건 허브로 돌아간다", "case_hub")
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
                "누가 날 해친 게 아니야. 그런데 그 순간엔 누군가가 있다고 믿고 싶었어.",
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
                "문 밖의 인기척을 범행으로 연결하는 건 쉽다. 하지만 실제 사고는 문보다 발밑에서 시작됐을 수 있다.",
                "bg_music_room_night.png",
                "ch_exorcist.png",
                List.of(
                        new Choice("김현진이 음악실에 들어와 해쳤다고 본다", "music_wrong", g -> g.suspicionScore++),
                        new Choice("전선에 걸려 넘어지며 손이 피아노 안으로 들어간 사고로 본다", "music_true", g -> {
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
                List.of(new Choice("과학실 사건으로 간다", "science_intro")),
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
                List.of(new Choice("과학실 사건으로 간다", "science_intro")),
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
                        new Choice("실험 노트와 용기를 조사한다", "science_lab"),
                        new Choice("사건 허브로 돌아간다", "case_hub")
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
                        new Choice("김현진이 약품을 바꿨다고 본다", "science_wrong", g -> g.suspicionScore++),
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
                "누군가 날 죽였다고 믿으면 조금은 덜 허무할 줄 알았는데... 아니었어.",
                "bg_science_explosion_mark.png",
                "ch_ghost_kim_junyeong_soft.png",
                List.of(new Choice("옥상 사건으로 간다", "rooftop_intro")),
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
                List.of(new Choice("주다영의 마지막 사건으로 간다", "rooftop_intro")),
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
                        new Choice("음악실 사건", "music_intro", g -> {}, g -> !g.musicSolved),
                        new Choice("과학실 사건", "science_intro", g -> {}, g -> !g.scienceSolved),
                        new Choice("옥상 사건", "rooftop_intro"),
                        new Choice("최종 추리로 넘어간다", "final_board", g -> {}, g -> g.poolSolved && g.musicSolved && g.scienceSolved)
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
                        new Choice("최종 추리 보드로 간다", "final_board")
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
                List.of(new Choice("최종 추리 보드로 간다", "final_board")),
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
                List.of(new Choice("처음부터 다시 시작한다", "prologue_arrival", GameState::reset)),
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
                List.of(new Choice("처음부터 다시 시작한다", "prologue_arrival", GameState::reset)),
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
                List.of(new Choice("처음부터 다시 시작한다", "prologue_arrival", GameState::reset)),
                null
        ));
    }

    private void resetState() {
        state.reset();
    }

    private void showScene(String id) {
        Scene scene = scenes.get(id);
        if (scene == null) {
            return;
        }
        currentScene = scene;
        if (scene.onEnter != null) {
            scene.onEnter.accept(state);
        }

        titleLabel.setText(scene.title);
        chapterLabel.setText(scene.chapter);
        narrationArea.setText(scene.narration);
        narrationArea.setCaretPosition(0);
        speakerLabel.setText(scene.speaker.isEmpty() ? " " : scene.speaker);
        startDialogueAnimation(scene.dialogue);
        statusLabel.setText(buildStatusText());

        layoutSceneLayers();
        refreshImages();
        SwingUtilities.invokeLater(() -> {
            layoutSceneLayers();
            refreshImages();
            scenePanel.repaint();
        });

        choicesPanel.removeAll();
        for (Choice choice : scene.choices) {
            if (!choice.isVisible(state)) {
                continue;
            }
            choicesPanel.add(createChoiceButton(choice));
        }
        choicesPanel.revalidate();
        choicesPanel.repaint();
    }

    private JButton createChoiceButton(Choice choice) {
        JButton button = new JButton(choice.label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setFont(new Font("Serif", Font.BOLD, 15));
        button.setBackground(BUTTON);
        button.setForeground(new Color(236, 240, 246));
        button.setBorder(choiceBorder(ACCENT_SOFT, new Color(255, 255, 255, 16)));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(BUTTON_HOVER);
                button.setBorder(choiceBorder(ACCENT, new Color(255, 255, 255, 38)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(BUTTON);
                button.setBorder(choiceBorder(ACCENT_SOFT, new Color(255, 255, 255, 16)));
            }
        });
        button.addActionListener(e -> {
            choice.effect.accept(state);
            showScene(choice.nextSceneId);
        });
        return button;
    }

    private Border choiceBorder(Color outer, Color inner) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(outer, 1),
                        BorderFactory.createLineBorder(inner, 1)
                ),
                new EmptyBorder(11, 14, 11, 14)
        );
    }

    private String buildStatusText() {
        return "truth=" + state.truthScore
                + "  suspect=" + state.suspicionScore
                + "  clues=" + state.cluesFound
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
        dialogueArea.setText("");
        if (fullText == null || fullText.isEmpty()) {
            return;
        }
        final int[] index = {0};
        dialogueTimer = new Timer(18, e -> {
            index[0]++;
            dialogueArea.setText(fullText.substring(0, index[0]));
            dialogueArea.setCaretPosition(dialogueArea.getDocument().getLength());
            if (index[0] >= fullText.length()) {
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
        int characterHeight = Math.max(1, (int) Math.round(height * 0.82));
        int characterY = Math.max(0, height - characterHeight);
        characterLabel.setBounds(0, characterY, width, characterHeight);
        overlayPanel.setBounds(0, 0, width, height);
        scenePanel.setComponentZOrder(backgroundLabel, 2);
        scenePanel.setComponentZOrder(characterLabel, 1);
        scenePanel.setComponentZOrder(overlayPanel, 0);
    }

    private void refreshImages() {
        if (currentScene == null) {
            return;
        }
        int width = Math.max(1, scenePanel.getWidth());
        int height = Math.max(1, scenePanel.getHeight());
        setImage(backgroundLabel, currentScene.backgroundImage, width, height, "BG: " + currentScene.backgroundImage, true);
        setImage(characterLabel, currentScene.characterImage, (int) Math.round(width * 0.74), (int) Math.round(height * 0.78),
                currentScene.characterImage == null || currentScene.characterImage.isEmpty() ? "" : "CH: " + currentScene.characterImage, false);
    }

    private void setImage(JLabel label, String fileName, int targetWidth, int targetHeight, String fallbackText, boolean cover) {
        if (fileName == null || fileName.isEmpty()) {
            label.setIcon(null);
            label.setText(fallbackText == null ? "" : fallbackText);
            return;
        }
        String path = "assets/images/" + fileName;
        try {
            BufferedImage image = ImageIO.read(new File(path));
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }

    private static class GameState {
        boolean poolSolved;
        boolean musicSolved;
        boolean scienceSolved;
        int truthScore;
        int suspicionScore;
        int cluesFound;
        String finalChoice = "";

        void unlockClue(String clueName) {
            cluesFound++;
        }

        void reset() {
            poolSolved = false;
            musicSolved = false;
            scienceSolved = false;
            truthScore = 0;
            suspicionScore = 0;
            cluesFound = 0;
            finalChoice = "";
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
        final Consumer<GameState> onEnter;

        Scene(String title, String chapter, String narration, String speaker, String dialogue,
              String backgroundImage, String characterImage, List<Choice> choices, Consumer<GameState> onEnter) {
            this.title = title;
            this.chapter = chapter;
            this.narration = narration;
            this.speaker = speaker;
            this.dialogue = dialogue;
            this.backgroundImage = backgroundImage;
            this.characterImage = characterImage;
            this.choices = choices;
            this.onEnter = onEnter;
        }
    }

    private static class Choice {
        final String label;
        final String nextSceneId;
        final Consumer<GameState> effect;
        final Predicate<GameState> visibility;

        Choice(String label, String nextSceneId) {
            this(label, nextSceneId, g -> {}, g -> true);
        }

        Choice(String label, String nextSceneId, Consumer<GameState> effect) {
            this(label, nextSceneId, effect, g -> true);
        }

        Choice(String label, String nextSceneId, Consumer<GameState> effect, Predicate<GameState> visibility) {
            this.label = label;
            this.nextSceneId = nextSceneId;
            this.effect = effect;
            this.visibility = visibility;
        }

        boolean isVisible(GameState state) {
            return visibility.test(state);
        }
    }
}
