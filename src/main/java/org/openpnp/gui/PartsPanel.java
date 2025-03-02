/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.openpnp.events.FeederSelectedEvent;
import org.openpnp.events.JobLoadedEvent;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.support.ActionGroup;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.IdentifiableListCellRenderer;
import org.openpnp.gui.support.IdentifiableTableCellRenderer;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.PackagesComboBoxModel;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.gui.tablemodel.PartsTableModel;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.FiducialLocator;
import org.openpnp.spi.PartAlignment;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Serializer;

import com.google.common.eventbus.Subscribe;

@SuppressWarnings("serial")
public class PartsPanel extends JPanel implements WizardContainer {


    private static final String PREF_DIVIDER_POSITION = "PartsPanel.dividerPosition";
    private static final int PREF_DIVIDER_POSITION_DEF = -1;
    private Preferences prefs = Preferences.userNodeForPackage(PartsPanel.class);

    final private Configuration configuration;
    final private Frame frame;

    private PartsTableModel tableModel;
    private TableRowSorter<PartsTableModel> tableSorter;
    JCheckBox cbShowOnlyPartsUsedInJob;
    private JTextField searchTextField;
    private JTable table;
    private ActionGroup singleSelectionActionGroup;
    private ActionGroup multiSelectionActionGroup;

    public PartsPanel(Configuration configuration, Frame frame) {
        this.configuration = configuration;
        this.frame = frame;

        singleSelectionActionGroup = new ActionGroup(deletePartAction, pickPartAction, copyPartToClipboardAction, showPackageAction);
        singleSelectionActionGroup.setEnabled(false);
        multiSelectionActionGroup = new ActionGroup(deletePartAction);
        multiSelectionActionGroup.setEnabled(false);

        setLayout(new BorderLayout(0, 0));
        tableModel = new PartsTableModel();
        tableSorter = new TableRowSorter<>(tableModel);

        JPanel toolbarAndSearch = new JPanel();
        add(toolbarAndSearch, BorderLayout.NORTH);
        toolbarAndSearch.setLayout(new BorderLayout(0, 0));

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolbarAndSearch.add(toolBar);

        JPanel panel_1 = new JPanel();
        toolbarAndSearch.add(panel_1, BorderLayout.EAST);
        
        cbShowOnlyPartsUsedInJob = new JCheckBox(showOnlyPartsUsedInJobAction);
        cbShowOnlyPartsUsedInJob.setMargin(new Insets(0, 0, 0, 25));
        panel_1.add(cbShowOnlyPartsUsedInJob);

        JLabel lblSearch = new JLabel("Search");
        panel_1.add(lblSearch);

        searchTextField = new JTextField();
        searchTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterParts();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
            	filterParts();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            	filterParts();
            }
        });
        panel_1.add(searchTextField);
        searchTextField.setColumns(15);

        JComboBox packagesCombo = new JComboBox(new PackagesComboBoxModel());
        packagesCombo.setMaximumRowCount(20);
        packagesCombo.setRenderer(new IdentifiableListCellRenderer<org.openpnp.model.Package>());

        JSplitPane splitPane = new JSplitPane();
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPane.setContinuousLayout(true);
        splitPane
                .setDividerLocation(prefs.getInt(PREF_DIVIDER_POSITION, PREF_DIVIDER_POSITION_DEF));
        splitPane.addPropertyChangeListener("dividerLocation", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                prefs.putInt(PREF_DIVIDER_POSITION, splitPane.getDividerLocation());
            }
        });
        add(splitPane, BorderLayout.CENTER);

        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);

        table = new AutoSelectTextTable(tableModel);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultEditor(org.openpnp.model.Package.class,
                new DefaultCellEditor(packagesCombo));
        table.setDefaultRenderer(org.openpnp.model.Package.class,
                new IdentifiableTableCellRenderer<org.openpnp.model.Package>());

        table.setRowSorter(tableSorter);
        table.getTableHeader().setDefaultRenderer(new MultisortTableHeaderCellRenderer());
        splitPane.setLeftComponent(new JScrollPane(table));
        splitPane.setRightComponent(tabbedPane);
        
        toolBar.add(newPartAction);
        toolBar.add(deletePartAction);
        toolBar.addSeparator();
        toolBar.add(pickPartAction);
        
        toolBar.addSeparator();
        JButton btnNewButton = new JButton(copyPartToClipboardAction);
        btnNewButton.setHideActionText(true);
        toolBar.add(btnNewButton);
        
        JButton btnNewButton_1 = new JButton(pastePartToClipboardAction);
        btnNewButton_1.setHideActionText(true);
        toolBar.add(btnNewButton_1);
        
        JButton btnShowPackage = new JButton(showPackageAction);
        btnShowPackage.setHideActionText(true);
        toolBar.add(btnShowPackage);

        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }

                List<Part> selections = getSelections();

                if (selections.size() > 1) {
                    singleSelectionActionGroup.setEnabled(false);
                    multiSelectionActionGroup.setEnabled(true);
                }
                else {
                    multiSelectionActionGroup.setEnabled(false);
                    singleSelectionActionGroup.setEnabled(!selections.isEmpty());
                }

                Part part = getSelection();

                int selectedTab = tabbedPane.getSelectedIndex();
                tabbedPane.removeAll();

                if (part != null) {
                    tabbedPane.add("Settings", new JScrollPane(new PartSettingsPanel(part)));

                    for (PartAlignment partAlignment : Configuration.get().getMachine().getPartAlignments()) {
                        Wizard wizard = partAlignment.getPartConfigurationWizard(part);
                        if (wizard != null) {
                            JPanel panel = new JPanel();
                            panel.setLayout(new BorderLayout());
                            panel.add(wizard.getWizardPanel());
                            tabbedPane.add(wizard.getWizardName(), new JScrollPane(panel));
                            wizard.setWizardContainer(PartsPanel.this);
                        }
                    }
                    
                    FiducialLocator fiducialLocator =
                            Configuration.get().getMachine().getFiducialLocator();
                    Wizard wizard = fiducialLocator.getPartConfigurationWizard(part);
                    if (wizard != null) {
                        JPanel panel = new JPanel();
                        panel.setLayout(new BorderLayout());
                        panel.add(wizard.getWizardPanel());
                        tabbedPane.add(wizard.getWizardName(), new JScrollPane(panel));
                        wizard.setWizardContainer(PartsPanel.this);
                    }
                }

                if (selectedTab >= 0 && selectedTab < tabbedPane.getTabCount()) {
                    tabbedPane.setSelectedIndex(selectedTab);
                }

                revalidate();
                repaint();
            }
        });
        
        // For jobLoaded function trigger to refresh parts list
        Configuration.get().getBus().register(this);
    }

    /**
     * Activate the Parts tab and show the Part for the specified Feeder. 
     * 
     * @param feeder
     */
    public void showPartForFeeder(Feeder feeder) {
        MainFrame.get().showTab("Parts");
        searchTextField.setText("");
        cbShowOnlyPartsUsedInJob.setSelected(false);
        filterParts();

        Part part = feeder.getPart();

        table.getSelectionModel().clearSelection();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getPart(i) == part) {
                int index = table.convertRowIndexToView(i);
                table.getSelectionModel().setSelectionInterval(index, index);
                table.scrollRectToVisible(new Rectangle(table.getCellRect(index, 0, true)));
                break;
            }
        }
    }
    
    private Part getSelection() {
        List<Part> selections = getSelections();
        if (selections.size() != 1) {
            return null;
        }
        return selections.get(0);
    }

    private List<Part> getSelections() {
        List<Part> selections = new ArrayList<>();
        for (int selectedRow : table.getSelectedRows()) {
            selectedRow = table.convertRowIndexToModel(selectedRow);
            selections.add(tableModel.getPart(selectedRow));
        }
        return selections;
    }

	private void filterParts() {
		ArrayList<RowFilter<PartsTableModel, Object>> andFilters = new ArrayList<RowFilter<PartsTableModel, Object>>();

		if (searchTextField.getText().trim().length() > 0) {
			try {
				RowFilter<PartsTableModel, Object> rfSearch = null;
				rfSearch = RowFilter.regexFilter("(?i)" + searchTextField.getText().trim());
				andFilters.add(rfSearch);
			} 
			catch (PatternSyntaxException e) {
				Logger.warn("Search failed", e);
			}
		}

		if (cbShowOnlyPartsUsedInJob.isSelected()) {
			List<BoardLocation> jobBoardLocations = MainFrame.get().getJobTab().getJob().getBoardLocations();
			if (jobBoardLocations.size() == 0) {
				MessageBoxes.infoBox("Filtering error", "Can't find job board. Make sure that job is loaded.");
				cbShowOnlyPartsUsedInJob.setSelected(false);
				return;
			}

			List<Placement> jobPlacements = jobBoardLocations.get(0).getBoard().getPlacements();
			if (jobPlacements.size() == 0) {
				MessageBoxes.infoBox("Filtering error", "Can't placements in job. Make sure that job is loaded.");
				cbShowOnlyPartsUsedInJob.setSelected(false);
				return;
			}

			ArrayList<String> partsId = new ArrayList<String>();
			for (Placement p : jobPlacements) {
				Part part = p.getPart();
				if (part != null) {
					partsId.add(part.getId());
				}
			}

			// Create new row filter that checks if part is in job
			andFilters.add(new RowFilter<PartsTableModel, Object>() {

				public boolean include(Entry<? extends PartsTableModel, ? extends Object> entry) {
					for (int i = entry.getValueCount() - 1; i >= 0; i--) {
						if (partsId.contains(entry.getStringValue(i))) {
							return true;
						}
					}
					return false;
				}
			});
		}
		
		tableSorter.setRowFilter(RowFilter.andFilter(andFilters));
	}

    public final Action newPartAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, "New Part...");
            putValue(SHORT_DESCRIPTION, "Create a new part, specifying it's ID.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (Configuration.get().getPackages().size() == 0) {
                MessageBoxes.errorBox(getTopLevelAncestor(), "Error",
                        "There are currently no packages defined in the system. Please create at least one package before creating a part.");
                return;
            }

            String id;
            while ((id = JOptionPane.showInputDialog(frame,
                    "Please enter an ID for the new part.")) != null) {
                if (configuration.getPart(id) != null) {
                    MessageBoxes.errorBox(frame, "Error", "Part ID " + id + " already exists.");
                    continue;
                }
                Part part = new Part(id);

                part.setPackage(Configuration.get().getPackages().get(0));

                configuration.addPart(part);
                tableModel.fireTableDataChanged();
                Helpers.selectLastTableRow(table);
                break;
            }
        }
    };

    public final Action deletePartAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, "Delete Part");
            putValue(SHORT_DESCRIPTION, "Delete the currently selected part.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Part> selections = getSelections();
            List<String> ids = selections.stream().map(Part::getId).collect(Collectors.toList());
            String formattedIds;
            if (ids.size() <= 3) {
                formattedIds = String.join(", ", ids);
            }
            else {
                formattedIds = String.join(", ", ids.subList(0, 3)) + ", and " + (ids.size() - 3) + " others";
            }
            
            int ret = JOptionPane.showConfirmDialog(getTopLevelAncestor(),
                    "Are you sure you want to delete " + formattedIds + "?",
                    "Delete " + selections.size() + " parts?", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.YES_OPTION) {
                for (Part part : selections) {
                    Configuration.get().removePart(part);
                }
            }
        }
    };

    public final Action pickPartAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.pick);
            putValue(NAME, "Pick Part");
            putValue(SHORT_DESCRIPTION, "Pick the selected part from the first available feeder.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Part part = getSelection();
                Feeder feeder = null;
                // find a feeder to feed
                for (Feeder f : Configuration.get().getMachine().getFeeders()) {
                    if (f.getPart() == part && f.isEnabled()) {
                        feeder = f;
                    }
                }
                if (feeder == null) {
                    throw new Exception("No valid feeder found for " + part.getId());
                }
                // Perform the whole Job like pick cycle as in the FeedersPanel. 
                FeedersPanel.pickFeeder(feeder);
            });
        }
	};

	private final Action showOnlyPartsUsedInJobAction = new AbstractAction() {
		{
			putValue(NAME, "Show only parts used in job");
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
	    	filterParts();
		}
	};

	public final Action copyPartToClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.copy);
            putValue(NAME, "Copy Part to Clipboard");
            putValue(SHORT_DESCRIPTION,
                    "Copy the currently selected part to the clipboard in text format.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            Part part = getSelection();
            if (part == null) {
                return;
            }
            try {
                Serializer s = Configuration.createSerializer();
                StringWriter w = new StringWriter();
                s.write(part, w);
                StringSelection stringSelection = new StringSelection(w.toString());
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
            }
            catch (Exception e) {
                MessageBoxes.errorBox(getTopLevelAncestor(), "Copy Failed", e);
            }
        }
    };
    
	public final Action showPackageAction = new AbstractAction() {
		{
			putValue(SMALL_ICON, Icons.arrowRight);
			putValue(NAME, "Show Part's Package");
            putValue(SHORT_DESCRIPTION, "Open part's package tab");
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
			if (getSelection() != null) {
				MainFrame.get().getPackagesTab().showPackageForPart(getSelection());
			}
		}
	};
    

    public final Action pastePartToClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.paste);
            putValue(NAME, "Create Part from Clipboard");
            putValue(SHORT_DESCRIPTION, "Create a new part from a definition on the clipboard.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                Serializer ser = Configuration.createSerializer();
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                String s = (String) clipboard.getData(DataFlavor.stringFlavor);
                StringReader r = new StringReader(s);
                Part part = ser.read(Part.class, s);
                for (int i = 0;; i++) {
                    if (Configuration.get().getPart(part.getId() + "-" + i) == null) {
                        part.setId(part.getId() + "-" + i);
                        Configuration.get().addPart(part);
                        break;
                    }
                }
                tableModel.fireTableDataChanged();
                Helpers.selectLastTableRow(table);
            }
            catch (Exception e) {
                MessageBoxes.errorBox(getTopLevelAncestor(), "Paste Failed", e);
            }
        }
    };
    

    @Subscribe
    public void jobLoaded(JobLoadedEvent event) {
    	filterParts();
    }
    
    @Override
    public void wizardCompleted(Wizard wizard) {}

    @Override
    public void wizardCancelled(Wizard wizard) {}
}
