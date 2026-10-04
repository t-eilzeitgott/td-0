package neontd.desktop;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import neontd.app.App;
import neontd.platform.KeyValueStore;
import neontd.platform.Platform;

/** Startet Neon TD als Desktop-Anwendung (Java2D in einem Swing-Fenster). F11 = Vollbild, Esc = Zurück. */
public final class DesktopMain {
    private DesktopMain() {
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(DesktopMain::start);
    }

    private static final class GamePanel extends JPanel {
        private final App app;
        private final Java2DGfx gfx = new Java2DGfx();
        private final long startNanos = System.nanoTime();
        private int lastW = -1;
        private int lastH = -1;
        private boolean mouseDown;

        GamePanel(App app) {
            this.app = app;
            setBackground(Color.BLACK);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (SwingUtilities.isRightMouseButton(e)) {
                        app.key("RightClick", false);
                        return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        mouseDown = true;
                        app.pointerDown(e.getX(), e.getY(), false);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (mouseDown && SwingUtilities.isLeftMouseButton(e)) {
                        mouseDown = false;
                        app.pointerUp(e.getX(), e.getY(), false);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    app.pointerMove(e.getX(), e.getY(), false, false);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    app.pointerMove(e.getX(), e.getY(), mouseDown, false);
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    app.wheel(e.getPreciseWheelRotation() * 60);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    String code = codeFor(e);
                    if (code != null) {
                        app.key(code, e.isControlDown() || e.isMetaDown());
                    }
                }
            });
        }

        private static String codeFor(KeyEvent e) {
            int k = e.getKeyCode();
            if (k >= KeyEvent.VK_A && k <= KeyEvent.VK_Z) {
                return "Key" + (char) k;
            }
            if (k >= KeyEvent.VK_0 && k <= KeyEvent.VK_9) {
                return "Digit" + (char) k;
            }
            switch (k) {
                case KeyEvent.VK_ESCAPE:
                    return "Escape";
                case KeyEvent.VK_SPACE:
                    return "Space";
                case KeyEvent.VK_ENTER:
                    return "Enter";
                case KeyEvent.VK_TAB:
                    return "Tab";
                case KeyEvent.VK_DELETE:
                case KeyEvent.VK_BACK_SPACE:
                    return "Delete";
                default:
                    return null;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(Color.BLACK);
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (getWidth() != lastW || getHeight() != lastH) {
                    lastW = getWidth();
                    lastH = getHeight();
                    app.resize(lastW, lastH, 0, 0, 0, 0);
                }
                gfx.begin(g2, getWidth(), getHeight());
                app.frame((System.nanoTime() - startNanos) / 1e9, gfx);
            } finally {
                g2.dispose();
            }
        }
    }

    private static App gameApp;

    /** Stand sichern, kurz auf einen laufenden Cloud-Abgleich warten, dann beenden. */
    private static void shutdown() {
        if (gameApp == null) {
            System.exit(0);
            return;
        }
        gameApp.suspend();
        final int[] ticks = {0};
        Timer t = new Timer(100, null);
        t.addActionListener(e -> {
            boolean busy = gameApp.cloud.status == neontd.save.GistSync.Status.WORKING;
            if (!busy || ++ticks[0] > 40) {
                System.exit(0);
            }
        });
        t.start();
    }

    private static void start() {
        final KeyValueStore store = new FileStore();
        final neontd.save.Http http = new JavaHttp();
        App app = new App(new Platform() {
            @Override
            public KeyValueStore store() {
                return store;
            }

            @Override
            public boolean touchPrimary() {
                return false;
            }

            @Override
            public boolean canQuit() {
                return true;
            }

            @Override
            public void quit() {
                shutdown();
            }

            @Override
            public double nowMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public neontd.save.Http http() {
                return http;
            }

            @Override
            public void prompt(String title, String initial, java.util.function.Consumer<String> result) {
                Object r = javax.swing.JOptionPane.showInputDialog(null, title, "Neon TD",
                        javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, initial);
                result.accept(r == null ? null : r.toString());
            }

            @Override
            public void copyText(String text, java.util.function.Consumer<Boolean> done) {
                try {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(text), null);
                    done.accept(true);
                } catch (RuntimeException e) {
                    done.accept(false);
                }
            }

            @Override
            public void openUrl(String url) {
                try {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                    }
                } catch (java.io.IOException | RuntimeException e) {
                    System.err.println("Konnte " + url + " nicht öffnen: " + e);
                }
            }
        });
        gameApp = app;
        JFrame frame = new JFrame("Neon TD");
        GamePanel panel = new GamePanel(app);
        panel.setPreferredSize(new Dimension(1280, 720));
        frame.setContentPane(panel);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown();
            }
        });
        frame.getContentPane().setBackground(Color.BLACK);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.requestFocusInWindow();

        // F11: Vollbild umschalten
        panel.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("F11"), "fs");
        panel.getActionMap().put("fs", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                GraphicsDevice dev = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                boolean full = dev.getFullScreenWindow() == frame;
                frame.dispose();
                frame.setUndecorated(!full);
                frame.setVisible(true);
                dev.setFullScreenWindow(full ? null : frame);
                panel.requestFocusInWindow();
            }
        });

        Timer timer = new Timer(1000 / 60, e -> {
            panel.repaint();
            java.awt.Toolkit.getDefaultToolkit().sync();
        });
        timer.setCoalesce(true);
        timer.start();
    }
}
