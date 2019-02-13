import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.StyleConstants;
import javax.swing.undo.UndoManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditorFilter extends DocumentFilter {
	private static final Logger logger = LoggerFactory.getLogger(EditorFilter.class);
	private static final Set<String> KEYWORDS = Set.of("beginfig", "begingroup", "btex", "cycle", "dashed", "def", "dir", "draw", "drawarrow", "drawdblarrow", "else", "elseif", "etex", "end",
		"enddef", "endfig", "endfor", "endgroup", "evenly", "exitif", "exitunless", "fi", "fill", "filldraw", "for", "forever", "forsuffixes", "fullcircle", "if", "label", "pencircle", "pickup",
		"reflectedabout", "reverse", "rotated", "rotatedaround", "save", "scaled", "shifted", "step", "transformed", "undraw", "unfill", "unfilldraw", "unitsquare", "until", "upto", "vardef",
		"verbatimtex", "withcolor", "withdots", "withpen", "xscaled", "yscaled");
	private static final Set<String> DATATYPES = Set.of("boolean", "cmykcolor", "color", "numeric", "pair", "path", "pen", "picture", "rgbcolor", "string", "transform");
	private static final int FONT_SIZE = 14;

	private final JTextPane editor;
	private final Runnable textChangedCallback;

	public EditorFilter(JTextPane editor, Runnable textChangedCallback) {
		this.editor = editor;
		this.textChangedCallback = textChangedCallback;

		editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE));

		// enable undo/redo
		UndoManager undoManager = new UndoManager();
		editor.getDocument().addUndoableEditListener(undoManager);
		editor.getInputMap().put(KeyStroke.getKeyStroke("ctrl Z"), "undo");
		editor.getActionMap().put("undo", new AbstractAction() {
			private static final long serialVersionUID = -1752629541638430326L;

			@Override
			public void actionPerformed(ActionEvent evt) {
				if (undoManager.canUndo()) {
					undoManager.undo();
				}
			}
		});
		editor.getInputMap().put(KeyStroke.getKeyStroke("ctrl R"), "redo");
		editor.getActionMap().put("redo", new AbstractAction() {
			private static final long serialVersionUID = 4115178550486171232L;

			@Override
			public void actionPerformed(ActionEvent evt) {
				if (undoManager.canRedo()) {
					undoManager.redo();
				}
			}
		});
	}

	@Override
	public void insertString(FilterBypass fb, int offs, String str, AttributeSet a) throws BadLocationException {
		textChangedCallback.run();
		highlightAndInsert(fb, offs, str);
	}

	@Override
	public void replace(DocumentFilter.FilterBypass fb, int offs, int len, String str, AttributeSet a) throws BadLocationException {
		textChangedCallback.run();
		super.remove(fb, offs, len);
		highlightAndInsert(fb, offs, str);
	}

	@Override
	public void remove(DocumentFilter.FilterBypass fb, int offs, int len) throws BadLocationException {
		textChangedCallback.run();
		boolean removingWhite = fb.getDocument().getText(offs, len).trim().isEmpty();

		super.remove(fb, offs, len);
		try {
			String indent = this.indent(offs, false);
			String smallerIndent = this.indent(offs, true);
			int newLineOffs = offs - indent.length() - 1;
			String maybeNewline = newLineOffs < 0 ? "" : editor.getDocument().getText(newLineOffs, 1);
			if (removingWhite && indent.length() > 0 && (maybeNewline.isEmpty() || maybeNewline.equals("\n"))) {
				super.remove(fb, newLineOffs + 1, indent.length());
				super.insertString(fb, newLineOffs + 1, smallerIndent, FontStyling.NORMAL.getAttributeSet());
			} else if (offs > 0) {
				highlightAndInsert(fb, offs, "");
			}
		} catch (BadLocationException e) {
			logger.error(e.getMessage(), e);
		}
	}

	// TODO: obsługa EditorState.COMMENT
	private void highlightAndInsert(FilterBypass filterBypass, int offset, String input) throws BadLocationException {

		// invoke automatic indentation only if a single <ENTER> is received.
		// In the case of pasting multiple lines, the original indentation
		// should be reserved.
		if (input.length() == 1 && input.charAt(0) == '\n') {
			try {
				String indent = this.indent(offset, false);
				super.insertString(filterBypass, offset, "\n" + indent, FontStyling.NORMAL.getAttributeSet());
			} catch (BadLocationException e) {
				logger.error(e.getMessage(), e);
			}
			return;
		}

		String prefix, suffix;
		// search for partial word before
		int from = offset;
		if (input.isEmpty() || !Character.isWhitespace(input.charAt(0))) {
			from--;
			while (from >= 0) {
				char c = filterBypass.getDocument().getText(from, 1).charAt(0);
				if (Character.isWhitespace(c)) {
					break;
				}
				from--;
			}
			from++;
		}
		prefix = filterBypass.getDocument().getText(from, offset - from);

		// search for partial word after
		int to = offset;
		if (input.isEmpty() || !Character.isWhitespace(input.charAt(input.length() - 1))) {
			int wholeLen = filterBypass.getDocument().getLength();
			while (to < wholeLen) {
				char c = filterBypass.getDocument().getText(to, 1).charAt(0);
				if (Character.isWhitespace(c)) {
					break;
				}
				to++;
			}
		}
		suffix = filterBypass.getDocument().getText(offset, to - offset);

		// removing the partial words
		offset -= prefix.length();
		super.remove(filterBypass, offset, prefix.length() + suffix.length());

		String[] words = input.split(" ", -1);
		words[0] = prefix + words[0];
		words[words.length - 1] += suffix;

		input = prefix + input + suffix;
		EditorState state = EditorState.NORMAL;
		if (offset > 0) {
			Color fgcolor = StyleConstants.getForeground(editor.getStyledDocument().getCharacterElement(offset - 1).getAttributes());
			if (fgcolor.equals(StyleConstants.getForeground(FontStyling.QUOTED.getAttributeSet()))) {
				state = EditorState.QUOTED;
			}
		}

		String token = "";
		char c;
		input += '\0';
		for (int i = 0; i < input.length(); i++) {
			c = input.charAt(i);
			EditorState newstate;
			if (c == '\0') {
				newstate = EditorState.DONE;
			} else if (state == EditorState.QUOTED || state == EditorState.LQUOTE) {
				newstate = (c == '\"') ? EditorState.RQUOTE : EditorState.QUOTED;
			} else if (Character.isDigit(c)) {
				newstate = EditorState.DIGITS;
			} else if (Character.isLetter(c)) {
				newstate = EditorState.WORD;
			} else if (c == '\"') {
				newstate = EditorState.LQUOTE;
			} else {
				newstate = EditorState.NORMAL;
			}

			if (state != newstate) {
				FontStyling tokenType;
				if (state == EditorState.DIGITS) {
					tokenType = FontStyling.DIGITS;
				} else if (state == EditorState.WORD && isKeyword(token)) {
					tokenType = FontStyling.KEYWORD;
				} else if (state == EditorState.WORD && isDatatype(token)) {
					tokenType = FontStyling.DATATYPE;
				} else if (state == EditorState.QUOTED) {
					tokenType = FontStyling.QUOTED;
				} else if (state == EditorState.COMMENT) {
					tokenType = FontStyling.COMMENT;
				} else {
					tokenType = FontStyling.NORMAL;
				}

				super.insertString(filterBypass, offset, token, tokenType.getAttributeSet());
				offset += token.length();
				state = newstate;
				token = "";
			}
			token += c;
		}

		editor.setCaretPosition(offset - suffix.length());
	}

	// return the indent (as string of whitespaces) of the current line
	// or closest shorter indent of previous lines if 'wantShorter' is true.
	private String indent(int offset, boolean wantShorter) throws BadLocationException {
		int pos = offset - 1;
		int posFirstNonWhite = offset;
		int firstIndentSize = -1;
		while (pos >= 0) {
			char ch = editor.getText(pos, 1).charAt(0);
			if (ch == '\n') {
				int previousIndentSize = posFirstNonWhite - pos - 1;
				if (!wantShorter || previousIndentSize < firstIndentSize) {
					break;
				}
				if (firstIndentSize < 0) {
					firstIndentSize = previousIndentSize;
				}
				posFirstNonWhite = pos;
			}
			if (!Character.isWhitespace(ch)) {
				posFirstNonWhite = pos;
			}
			pos--;
		}
		return editor.getText(pos + 1, posFirstNonWhite - pos - 1);
	}

	private boolean isKeyword(String word) {
		return KEYWORDS.contains(word);
	}

	private boolean isDatatype(String word) {
		return DATATYPES.contains(word);
	}
}