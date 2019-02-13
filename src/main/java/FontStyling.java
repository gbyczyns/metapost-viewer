import java.awt.Color;

import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

public enum FontStyling {
	KEYWORD(Color.BLUE, true, false),
	DIGITS(Color.MAGENTA, true, false),
	NORMAL(Color.BLACK, false, false),
	DATATYPE(Color.GREEN, true, false),
	QUOTED(Color.MAGENTA, true, false),
	COMMENT(Color.GRAY, false, true);

	private final SimpleAttributeSet attributeSet = new SimpleAttributeSet();

	private FontStyling(Color color, boolean bold, boolean italic) {
		StyleConstants.setForeground(attributeSet, color);
		StyleConstants.setBold(attributeSet, bold);
		StyleConstants.setItalic(attributeSet, italic);
	}

	public SimpleAttributeSet getAttributeSet() {
		return attributeSet;
	}
}