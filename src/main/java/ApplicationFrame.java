import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationFrame extends JFrame {

	private final static Logger logger = LoggerFactory.getLogger(ApplicationFrame.class);
	private final static long serialVersionUID = 5080413444904954347L;
	private final static int INIT_WIDTH = 1000;
	private final static int INIT_HEIGHT = 618;

	private final static int AUTO_PREVIEW_INTERVAL = 600;
	private final static String WORKING_DIRECTORY = System.getProperty("user.home") + File.separator + ".mpostviewer";
	private final Path previewMp;

	private final JToolBar toolbar = new JToolBar();
	private final JTextPane editor = new JTextPane();
	private final JLabel graph = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JButton saveButton = new JButton();
	private final JButton previewButton = new JButton();

	private final EditorFilter editorFilter = new EditorFilter(editor, () -> saveButton.setEnabled(true));

	private final String workingDirectoryPath;
	private final Timer timer;
	private final JRadioButton statusIcon = new JRadioButton();

	private final MetapostService metapostService;

	public ApplicationFrame() {
		super("Metapost Viewer");
		this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int topX = Math.max(0, (int) ((screenSize.getWidth() - ApplicationFrame.INIT_WIDTH) / 2));
		int topY = Math.max(0, (int) ((screenSize.getHeight() - ApplicationFrame.INIT_HEIGHT) / 3));
		this.setPreferredSize(new Dimension(ApplicationFrame.INIT_WIDTH, ApplicationFrame.INIT_HEIGHT));
		this.setLocation(topX, topY);
		this.pack();

		File workingDirectory = new File(ApplicationFrame.WORKING_DIRECTORY);

		if (workingDirectory.exists() || workingDirectory.mkdir()) {
			workingDirectoryPath = workingDirectory.getAbsolutePath();
			System.setProperty("user.dir", workingDirectoryPath);
		} else {
			workingDirectoryPath = System.getProperty("user.dir");
		}
		previewMp = Paths.get(workingDirectoryPath, "preview.mp");

		logger.info("Working directory: {}", workingDirectoryPath);
		metapostService = new MetapostService();

		saveButton.addActionListener(this::save);
		saveButton.setText("Save");
		saveButton.setToolTipText("Ctrl+S");
		toolbar.add(saveButton);

		previewButton.addActionListener(this::preview);
		previewButton.setText("Preview");
		previewButton.setToolTipText("Ctrl+P");
		toolbar.add(previewButton);

		statusIcon.setEnabled(false);
		statusIcon.setSelected(false);
		toolbar.add(statusIcon);

		statusLabel.setText("Working directory: " + System.getProperty("user.dir"));
		statusLabel.setFont(new Font(Font.SERIF, Font.PLAIN, 12));

		((AbstractDocument) editor.getDocument()).setDocumentFilter(editorFilter);
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
			if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) == KeyEvent.CTRL_DOWN_MASK) {
				if (e.getKeyCode() == KeyEvent.VK_S && isFocused() && e.getID() == KeyEvent.KEY_PRESSED) {
					saveButton.doClick();
				} else if (e.getKeyCode() == KeyEvent.VK_P && isFocused() && e.getID() == KeyEvent.KEY_PRESSED) {
					previewButton.doClick();
				}
			}
			return false;
		});

		timer = new Timer(ApplicationFrame.AUTO_PREVIEW_INTERVAL, this::preview);
		editor.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
					timer.stop();
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
					timer.restart();
				}
			}

		});

		// wczytanie pliku do edytora
		try {
			boolean templateLoaded = false;
			Reader reader;
			if (Files.exists(previewMp)) {
				reader = new FileReader(previewMp.toFile());
			} else {
				statusLabel.setText("Can't load " + previewMp + ", using template instead.");
				templateLoaded = true;
				InputStream is = this.getClass().getResourceAsStream("template.mp");
				reader = new InputStreamReader(is);
			}
			try (BufferedReader br = new BufferedReader(reader)) {
				String line;
				while ((line = br.readLine()) != null) {
					editor.getDocument().insertString(editor.getDocument().getLength(), line + "\n", null);
				}
			}
			if (templateLoaded) {
				editor.setCaretPosition(editor.getDocument().getLength() - 13);
			}
		} catch (IOException | BadLocationException e) {
			logger.error(e.getMessage(), e);
		}

		saveButton.setEnabled(false);

		JScrollPane editorScrollPane = new JScrollPane(editor);

		graph.setOpaque(true);
		graph.setBackground(Color.WHITE);
		ImageIcon imageIcon = new ImageIcon();
		graph.setIcon(imageIcon);
		graph.setHorizontalAlignment(SwingConstants.CENTER);
		graph.setVerticalAlignment(SwingConstants.CENTER);

		JScrollPane imagePane = new JScrollPane(graph);

		editorScrollPane.setPreferredSize(new Dimension(ApplicationFrame.INIT_WIDTH / 2, ApplicationFrame.INIT_HEIGHT));

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScrollPane, imagePane);

		this.getContentPane().add(toolbar, BorderLayout.PAGE_START);
		this.getContentPane().add(splitPane, BorderLayout.CENTER);
		this.getContentPane().add(statusLabel, BorderLayout.PAGE_END);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				editor.requestFocusInWindow();
			}

			@Override
			public void windowClosing(WindowEvent e) {
				if (saveButton.isEnabled()) {
					int response = JOptionPane.showConfirmDialog(ApplicationFrame.this, "Save file before exit?", "Closing...", JOptionPane.YES_NO_OPTION);
					if (response == JOptionPane.YES_OPTION) {
						saveEditorContentsToFile(previewMp);
					}
				}
			}
		});
	}

	private void saveEditorContentsToFile(Path targetFile) {
		try (FileWriter fileWriter = new FileWriter(targetFile.toFile())) {
			fileWriter.write(editor.getText());
			saveButton.setEnabled(false);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void save(ActionEvent event) {
		saveEditorContentsToFile(previewMp);
		statusLabel.setText("File saved: " + previewMp);
		editor.requestFocusInWindow();
	}

	private void preview(ActionEvent event) {
		timer.stop();
		saveEditorContentsToFile(previewMp);

		EventQueue.invokeLater(() -> {
			try {
				statusLabel.setText("Generating preview of " + previewMp);
				byte[] png = metapostService.renderMetapostToPng(previewMp);

				statusLabel.setText("Loading preview image...");
				ImageIcon img = new ImageIcon(png);
				graph.setIcon(img);
				graph.repaint();
				statusLabel.setText("Preview loaded");
				statusIcon.setBackground(Color.GREEN);
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
				statusLabel.setText(e.getMessage());
				statusIcon.setBackground(Color.RED);
			}
			editor.requestFocusInWindow();
		});
	}
}