package fiji.util;

import ij.CommandListener;
import ij.Executer;
import ij.IJ;
import ij.Menus;
import ij.Prefs;

import ij.gui.GenericDialog;

import ij.plugin.PlugIn;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class Recent_Commands implements ActionListener, CommandListener, KeyListener, ListSelectionListener, PlugIn {
	protected static int LIST_SIZE = 8;
	protected static int MAX_LRU_SIZE = 100;
	protected final static String PREFS_KEY = "recent.command";

	public void run(String arg) {
		readPrefs();
		if ("install".equals(arg))
			install();
		else
			runInteractively();
	}

	public void install() {
		Executer.addCommandListener(this);
		Menus.installPlugin(getClass().getName(),
			Menus.SHORTCUTS_MENU, "*recent commands", "9",
			IJ.getInstance());
	}

	JDialog dialog;
	JList mostRecent, mostFrequent;
	JButton okay, cancel, options;

	public void runInteractively() {
		Vector recent = getMostRecent(LIST_SIZE);
		if (recent.size() == 0) {
			JOptionPane.showMessageDialog(IJ.getInstance(),
				"No recent commands available!");
			return;
		}

		mostRecent = makeJList(recent);
		mostFrequent = makeJList(getMostFrequent(LIST_SIZE));
		mostRecent.setSelectedIndex(0);
		mostFrequent.clearSelection();

		dialog = new JDialog(IJ.getInstance(), "Recent Commands", true);
		dialog.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = c.HORIZONTAL;
		c.gridy = c.gridx = 0;
		dialog.add(new JLabel("Recent Commands:"), c);
		c.gridy++; c.gridx = 0;
		dialog.add(mostRecent, c);
		c.gridy++; c.gridx = 0;
		dialog.add(new JLabel("Frequently used Commands:"), c);
		c.gridy++; c.gridx = 0;
		dialog.add(mostFrequent, c);
		okay = new JButton("OK");
		okay.addActionListener(this);
		cancel = new JButton("Cancel");
		cancel.addActionListener(this);
		options = new JButton("Options");
		options.addActionListener(this);
		JPanel panel = new JPanel();
		panel.add(okay);
		panel.add(cancel);
		panel.add(options);
		c.gridy++; c.gridx = 0;
		dialog.add(panel, c);
		dialog.pack();
		dialog.setVisible(true);
	}

	JList makeJList(Vector items) {
		JList list = new JList(items);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener(this);
		list.addKeyListener(this);
		return list;
	}

	public void valueChanged(ListSelectionEvent e) {
		if (e.getSource() == mostRecent) {
			if (mostRecent.getSelectedIndex() >= 0)
				mostFrequent.clearSelection();
		}
		else {
			if (mostFrequent.getSelectedIndex() >= 0)
				mostRecent.clearSelection();
		}
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source == okay)
			runSelectedCommand();
		else if (source == options) {
			showOptionsDialog();
			return;
		}
		dialog.dispose();
	}

	public void keyPressed(KeyEvent e) {
		int key = e.getKeyCode();
		if (key == KeyEvent.VK_ESCAPE)
			dialog.dispose();
		else if (key == KeyEvent.VK_ENTER) {
			runSelectedCommand();
			dialog.dispose();
		}
		else if (e.getSource() == mostRecent) {
			if (key == KeyEvent.VK_DOWN &&
					mostRecent.getSelectedIndex() == mostRecent.getModel().getSize() - 1) {
				mostFrequent.setSelectedIndex(0);
				mostFrequent.requestFocus();
			}
			else if (key == KeyEvent.VK_UP &&
					mostRecent.getSelectedIndex() == 0) {
				mostFrequent.setSelectedIndex(mostFrequent.getModel().getSize() - 1);
				mostFrequent.requestFocus();
			}
		}
		else if (e.getSource() == mostFrequent) {
			if (key == KeyEvent.VK_UP &&
					mostFrequent.getSelectedIndex() == 0) {
				mostRecent.setSelectedIndex(mostRecent.getModel().getSize() - 1);
				mostRecent.requestFocus();
			}
			else if (key == KeyEvent.VK_DOWN &&
					mostFrequent.getSelectedIndex() == mostFrequent.getModel().getSize() - 1) {
				mostRecent.setSelectedIndex(0);
				mostRecent.requestFocus();
			}
		}
	}

	public void keyReleased(KeyEvent e) {}
	public void keyTyped(KeyEvent e) {}

	public String commandExecuting(String command) {
		if (command.equals("*recent commands") ||
				command.equals("Repeat a Recent Command"))
			return command;
		int listIndex = getListIndex();
		Prefs.set(PREFS_KEY + listIndex, command);
		listIndex = ((listIndex + 1) % MAX_LRU_SIZE);
		Prefs.set(PREFS_KEY + ".lastIndex", "" + listIndex);
		return command;
	}

	protected void runSelectedCommand() {
		String command = (String)mostRecent.getSelectedValue();
		if (command == null)
			command = (String)mostFrequent.getSelectedValue();
		if (command != null)
			new Executer(command, null);
	}

	protected int getListIndex() {
		String value = Prefs.get(PREFS_KEY + ".lastIndex", "0");
		return "".equals(value) ? 0 : Integer.parseInt(value);
	}

	protected Vector getMostRecent(int maxCount) {
		Vector result = new Vector();
		int listIndex = getListIndex();
		for (int i = 0; i < maxCount; i++) {
			listIndex = ((listIndex - 1 + MAX_LRU_SIZE) % MAX_LRU_SIZE);
			String command = Prefs.get(PREFS_KEY + listIndex, null);
			if (command == null)
				break;
			result.add(command);
		}
		return result;
	}

	protected Vector getMostFrequent(int maxCount) {
		Vector result = new Vector();
		final Map<String, Integer> map = new HashMap<String, Integer>();
		for (int i = 0; i < MAX_LRU_SIZE; i++) {
			String command = Prefs.get(PREFS_KEY + i, null);
			if (command == null)
				break;
			Integer value = map.get(command);
			if (value == null) {
				map.put(command, new Integer(1));
				result.add(command);
			}
			else
				map.put(command, new Integer(value.intValue() + 1));
		}
		Collections.sort(result, new Comparator<String>() {
			public int compare(String c1, String c2) {
				return map.get(c2).intValue()
					- map.get(c1).intValue();
			}

			public boolean equals(Object other) {
				return false;
			}
		});
		return new Vector(result.subList(0, Math.min(result.size(), maxCount)));
	}

	void readPrefs() {
		LIST_SIZE = (int)Prefs.get(PREFS_KEY + ".list-size", LIST_SIZE);
		MAX_LRU_SIZE = (int)Prefs.get(PREFS_KEY + ".max-lru-size", MAX_LRU_SIZE);
	}

	void showOptionsDialog() {
		GenericDialog gd = new GenericDialog("Recent Command Options");
		gd.addNumericField("list_size", LIST_SIZE, 0);
		gd.addNumericField("history_size", MAX_LRU_SIZE, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		int value = (int)gd.getNextNumber();
		if (value != LIST_SIZE) {
			LIST_SIZE = value;
			Prefs.set(PREFS_KEY + ".list-size", LIST_SIZE);
		}
		value = (int)gd.getNextNumber();
		if (value != MAX_LRU_SIZE) {
			MAX_LRU_SIZE = value;
			Prefs.set(PREFS_KEY + ".max-lru-size", MAX_LRU_SIZE);
		}
		mostRecent.setListData(getMostRecent(LIST_SIZE));
		mostFrequent.setListData(getMostFrequent(LIST_SIZE));
		dialog.pack();
	}
}
