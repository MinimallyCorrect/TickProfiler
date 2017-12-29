package org.minimallycorrect.tickprofiler.util;

import java.util.*;

import org.minimallycorrect.tickprofiler.util.stringfillers.StringFiller;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;

public class TableFormatter {
	private static final int POW10[] = {1, 10, 100, 1000, 10000, 100000, 1000000};
	public final StringBuilder sb = new StringBuilder();
	private final StringFiller stringFiller;
	private final List<String> currentHeadings = new ArrayList<>();
	private final List<String> currentData = new ArrayList<>();
	public String tableSeparator = "";
	private String splitter = " | ";
	private String headingSplitter = " | ";
	private String headingColour = "";
	private List<List<Map<String, String>>> tables;

	public TableFormatter(ICommandSender commandSender) {
		this(commandSender instanceof Entity ? StringFiller.CHAT : StringFiller.FIXED_WIDTH, false);
	}

	public TableFormatter(StringFiller stringFiller, boolean recordTables) {
		this.stringFiller = stringFiller;
		tables = recordTables ? new ArrayList<>() : null;
		if (stringFiller == StringFiller.CHAT) {
			splitter = " " + ChatFormat.YELLOW + '|' + ChatFormat.RESET + ' ';
			headingSplitter = splitter;
			headingColour = String.valueOf(ChatFormat.DARK_GREEN);
		}
	}

	/* http://stackoverflow.com/a/10554128/250076 */
	public static String formatDoubleWithPrecision(double val, int precision) {
		if (Double.isInfinite(val) || Double.isNaN(val)) {
			return Double.toString(val);
		}
		StringBuilder sb = new StringBuilder();
		if (val < 0) {
			sb.append('-');
			val = -val;
		}
		int exp = POW10[precision];
		long lval = (long) (val * exp + 0.5);
		sb.append(lval / exp).append('.');
		long fval = lval % exp;
		for (int p = precision - 1; p > 0 && fval < POW10[p]; p--) {
			sb.append('0');
		}
		sb.append(fval);
		return sb.toString();
	}

	public TableFormatter heading(String text) {
		currentHeadings.add(text);
		return this;
	}

	public TableFormatter row(String data) {
		currentData.add(data);
		return this;
	}

	public TableFormatter row(Object data) {
		return row(String.valueOf(data));
	}

	public TableFormatter row(int data) {
		return row(String.valueOf(data));
	}

	public TableFormatter row(long data) {
		return row(String.valueOf(data));
	}

	public TableFormatter row(double data) {
		currentData.add(formatDoubleWithPrecision(data, 3));
		return this;
	}

	public void finishTable() {
		int rowIndex = 0;
		int rowCount = currentHeadings.size();
		double[] rowLengths = new double[rowCount];
		getMaxLengths(rowLengths, rowIndex, rowCount, currentHeadings);
		getMaxLengths(rowLengths, rowIndex, rowCount, currentData);
		String cSplit = "";
		for (String heading : currentHeadings) {
			sb.append(cSplit).append(headingColour).append(stringFiller.fill(heading, rowLengths[rowIndex % rowCount]));
			cSplit = headingSplitter;
			rowIndex++;
		}
		sb.append('\n');
		cSplit = "";
		rowIndex = 0;
		ArrayList<Map<String, String>> table = null;
		if (tables != null) {
			table = new ArrayList<>();
			tables.add(table);
		}
		HashMap<String, String> entry = null;
		for (String data : currentData) {
			if (rowIndex % rowCount == 0 && table != null) {
				entry = new HashMap<>();
				table.add(entry);
			}
			sb.append(cSplit).append(stringFiller.fill(data, rowLengths[rowIndex % rowCount]));
			cSplit = splitter;
			if (entry != null) {
				entry.put(currentHeadings.get(rowIndex % rowCount), data);
			}
			rowIndex++;
			if (rowIndex % rowCount == 0 && rowIndex != currentData.size()) {
				sb.append('\n');
				cSplit = "";
			}
		}
		sb.append(tableSeparator);
		currentHeadings.clear();
		currentData.clear();
	}

	private void getMaxLengths(double[] rowLengths, int rowIndex, int rowCount, Iterable<String> stringIterable) {
		for (String data : stringIterable) {
			double length = stringFiller.getLength(data);
			if (rowLengths[rowIndex % rowCount] < length) {
				rowLengths[rowIndex % rowCount] = length;
			}
			rowIndex++;
		}
	}

	@Override
	public String toString() {
		return sb.toString();
	}

	public List<List<Map<String, String>>> getTables() {
		return tables;
	}
}
