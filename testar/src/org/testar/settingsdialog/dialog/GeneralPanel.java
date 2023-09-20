/***************************************************************************************************
*
* Copyright (c) 2013 - 2020 Universitat Politecnica de Valencia - www.upv.es
* Copyright (c) 2018 - 2020 Open Universiteit - www.ou.nl
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice,
* this list of conditions and the following disclaimer.
* 2. Redistributions in binary form must reproduce the above copyright
* notice, this list of conditions and the following disclaimer in the
* documentation and/or other materials provided with the distribution.
* 3. Neither the name of the copyright holder nor the names of its
* contributors may be used to endorse or promote products derived from
* this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
*******************************************************************************************************/


package org.testar.settingsdialog.dialog;

import org.testar.monkey.*;
import org.testar.settingsdialog.ProtocolEditor;
import org.testar.settingsdialog.SettingsDialog;
import org.testar.settingsdialog.SettingsPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

public class GeneralPanel extends SettingsPanel implements Observer {

  private static final long serialVersionUID = -7401834140061189752L;

  private Settings settings;
  private JComboBox<String> cboxSUTconnector;
  private JTextArea txtSutPath;

  private JLabel labelSutProcesses = new JLabel("SUT Processes");
  private JTextField sutProcessesField = new JTextField();

  private JSpinner spnNumSequences;
  private JSpinner spnSequenceLength;
  //private JCheckBox checkStopOnFault;
  private JComboBox<String> comboBoxProtocol;
  private JCheckBox compileCheckBox, checkActionVisualization;
  
  private JLabel labelAppName = new JLabel("Application name");
  private JLabel labelAppVersion = new JLabel("Application version");

  private JTextField applicationNameField = new JTextField();
  private JTextField applicationVersionField = new JTextField();

  private JLabel labelOverrideWebDriverDisplayScale = new JLabel("Override display scale");
  private JTextField overrideWebDriverDisplayScaleField = new JTextField();

  public GeneralPanel(SettingsDialog settingsDialog) {
    setLayout(null);

    addGeneralLabels();
    addGeneralControlsGlobal(settingsDialog);
    addGeneralControlsLocal();
  }

  private void addGeneralControlsGlobal(SettingsDialog settingsDialog) {
    cboxSUTconnector = new JComboBox<>();
    cboxSUTconnector.setModel(new DefaultComboBoxModel<>(new String[]{
        Settings.SUT_CONNECTOR_CMDLINE,
        Settings.SUT_CONNECTOR_PROCESS_NAME,
        Settings.SUT_CONNECTOR_WINDOW_TITLE,
        Settings.SUT_CONNECTOR_WEBDRIVER
    }));
    cboxSUTconnector.setSelectedIndex(0);
    cboxSUTconnector.setBounds(114, 12, 171, 25);
    cboxSUTconnector.setToolTipText(ToolTipTexts.sutConnectorTTT);
    cboxSUTconnector.setMaximumRowCount(3);
    add(cboxSUTconnector);

    txtSutPath = new JTextArea();
    txtSutPath.setLineWrap(true);
    txtSutPath.setToolTipText(ToolTipTexts.sutPathTTT);

    setSutProcessesVisibility(false);
    labelSutProcesses.setBounds(10, 133, 150, 27);
    labelSutProcesses.setToolTipText(ToolTipTexts.sutProcessesTTT);
    add(labelSutProcesses);
    sutProcessesField.setBounds(160, 133, 450, 27);
    sutProcessesField.setToolTipText(ToolTipTexts.sutProcessesTTT);
    add(sutProcessesField);

    spnNumSequences = new JSpinner();
    spnNumSequences.setBounds(160, 165, 71, 25);
    spnNumSequences.setModel(new SpinnerNumberModel(1, 1, null, 1));
    spnNumSequences.setToolTipText(ToolTipTexts.nofSequencesTTT);
    add(spnNumSequences);

    spnSequenceLength = new JSpinner();
    spnSequenceLength.setBounds(160, 205, 71, 25);
    spnSequenceLength.setModel(new SpinnerNumberModel(999, 1, null, 1));
    spnSequenceLength.setToolTipText(ToolTipTexts.sequencesActionsTTT);
    add(spnSequenceLength);

    comboBoxProtocol = new JComboBox<>();
    comboBoxProtocol.setBounds(350, 165, 260, 25);
    String[] sutSettings = new File(Main.settingsDir)
        .list((current, name) -> new File(current, name).isDirectory());
    Arrays.sort(sutSettings);
    comboBoxProtocol.setModel(new DefaultComboBoxModel<>(sutSettings));
    comboBoxProtocol.setMaximumRowCount(sutSettings.length > 16 ? 16 : sutSettings.length);
    
    // Pass button click to settings dialog
    MyItemListener myItemListener = new MyItemListener();
    myItemListener.addObserver(settingsDialog);
    myItemListener.addObserver(this);
    comboBoxProtocol.addItemListener(myItemListener);
    comboBoxProtocol.setToolTipText(ToolTipTexts.comboBoxProtocolTTT);
    add(comboBoxProtocol);

    compileCheckBox = new JCheckBox("Always compile protocol");
    compileCheckBox.setBounds(286, 205, 192, 21);
    compileCheckBox.setToolTipText(ToolTipTexts.lblCompileTTT);
    add(compileCheckBox);

    /*checkStopOnFault = new JCheckBox("Stop Test on Fault");
    checkStopOnFault.setBounds(10, 240, 192, 21);
    checkStopOnFault.setToolTipText(checkStopOnFaultTTT);
    add(checkStopOnFault);*/

    checkActionVisualization = new JCheckBox("Visualize actions on GUI");
    checkActionVisualization.setBounds(10, 245, 192, 21);
    //checkActionVisualization.setToolTipText(checkStopOnFaultTTT);
    add(checkActionVisualization);
    
    labelAppName.setBounds(330, 245, 150, 27);
    labelAppName.setToolTipText(ToolTipTexts.applicationNameTTT);
    add(labelAppName);
    applicationNameField.setBounds(480, 245, 125, 27);
    applicationNameField.setToolTipText(ToolTipTexts.applicationNameTTT);
    add(applicationNameField);

    labelAppVersion.setBounds(330, 285, 150, 27);
    labelAppVersion.setToolTipText(ToolTipTexts.applicationVersionTTT);
    add(labelAppVersion);
    applicationVersionField.setBounds(480, 285, 125, 27);
    applicationVersionField.setToolTipText(ToolTipTexts.applicationVersionTTT);
    add(applicationVersionField);

    // Hide the override webdriver display scale fields by default, only show them when a webdriver protocol is selected.
    setOverrideWebDriverDisplayScaleVisibility(false);
    labelOverrideWebDriverDisplayScale.setBounds(330, 325, 150, 27);
    labelOverrideWebDriverDisplayScale.setToolTipText(ToolTipTexts.overrideWebDriverDisplayScaleTTT);
    add(labelOverrideWebDriverDisplayScale);
    overrideWebDriverDisplayScaleField.setBounds(480, 325, 125, 27);
    overrideWebDriverDisplayScaleField.setToolTipText(ToolTipTexts.overrideWebDriverDisplayScaleTTT);
    add(overrideWebDriverDisplayScaleField);
  }

  private void setOverrideWebDriverDisplayScaleVisibility(boolean isVisible){
    labelOverrideWebDriverDisplayScale.setVisible(isVisible);
    overrideWebDriverDisplayScaleField.setVisible(isVisible);
  }

  private void setSutProcessesVisibility(boolean isVisible){
	  labelSutProcesses.setVisible(isVisible);
	  sutProcessesField.setVisible(isVisible);
  }

  @Override
  public void update(Observable o, Object arg) {
    boolean showWidgets = arg.toString().contains("webdriver");
    setOverrideWebDriverDisplayScaleVisibility(showWidgets);
    setSutProcessesVisibility(arg.toString().contains("desktop"));
  }

  private void addGeneralControlsLocal() {
    JButton btnSutPath = new JButton("Select SUT");
    btnSutPath.setBounds(520, 11, 90, 25);
    btnSutPath.addActionListener(this::btnSutPathActionPerformed);
    btnSutPath.setToolTipText(ToolTipTexts.btnSelectSUTTTT);
    add(btnSutPath);

    JButton btnEditProtocol = new JButton("Edit Protocol");
    btnEditProtocol.setBounds(510, 205, 100, 25);
    btnEditProtocol.addActionListener(this::btnEditProtocolActionPerformed);
    btnEditProtocol.setToolTipText(ToolTipTexts.btnEditProtocolTTT);
    btnEditProtocol.setMaximumSize(new Dimension(160, 35));
    btnEditProtocol.setMinimumSize(new Dimension(160, 35));
    btnEditProtocol.setPreferredSize(new Dimension(160, 35));
    add(btnEditProtocol);

    JScrollPane scrollPane = new JScrollPane();
    scrollPane.setBounds(10, 42, 600, 90);
    add(scrollPane);
    scrollPane.setViewportView(txtSutPath);
  }

  private void addGeneralLabels() {
    JLabel lblSUTconnector = new JLabel("SUT connector:");
    lblSUTconnector.setBounds(10, 11, 97, 20);
    lblSUTconnector.setToolTipText(ToolTipTexts.sutConnectorTTT);
    add(lblSUTconnector);

    JLabel lblNofSequences = new JLabel("Number of Sequences:");
    lblNofSequences.setBounds(10, 170, 135, 14);
    lblNofSequences.setToolTipText(ToolTipTexts.nofSequencesTTT);
    add(lblNofSequences);

    JLabel lblSequenceActions = new JLabel("Sequence actions:");
    lblSequenceActions.setBounds(10, 210, 148, 14);
    lblSequenceActions.setToolTipText(ToolTipTexts.sequencesActionsTTT);
    add(lblSequenceActions);

    JLabel lblProtocol = new JLabel("Protocol:");
    lblProtocol.setBounds(286, 170, 64, 14);
    lblProtocol.setToolTipText(ToolTipTexts.comboBoxProtocolTTT);
    add(lblProtocol);
  }

  private void btnSutPathActionPerformed(ActionEvent evt) {
    JFileChooser fd = new JFileChooser();
    fd.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fd.setCurrentDirectory(new File(settings.get(ConfigTags.SUTConnectorValue)).getParentFile());

    if (fd.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      String file = fd.getSelectedFile().getAbsolutePath();

      if (settings.get(ConfigTags.SUTConnector)
          .equals(Settings.SUT_CONNECTOR_WEBDRIVER)) {
        // When using the WEB_DRIVER connector, only replace webdriver path
        String[] orgSettingParts = txtSutPath.getText().split(" ");
        orgSettingParts[0] = "\"" + file + "\"";
        txtSutPath.setText(String.join(" ", orgSettingParts));
      }
      else {
        // Set the text from settings in txtSutPath
        txtSutPath.setText(file);
      }
    }
  }

  private void btnEditProtocolActionPerformed(ActionEvent evt) {
    JDialog dialog = new ProtocolEditor(Main.settingsDir, settings.get(ConfigTags.ProtocolClass));
    dialog.setModalityType(JDialog.ModalityType.APPLICATION_MODAL);
    dialog.setVisible(true);
  }

  /**
   * Populate JPanelGeneral from Settings structure.
   *
   * @param settings The settings to load.
   */
  @Override
  public void populateFrom(final Settings settings) {
    this.settings = settings;

    cboxSUTconnector.setSelectedItem(settings.get(ConfigTags.SUTConnector));
    //checkStopOnFault.setSelected(settings.get(ConfigTags.StopGenerationOnFault));
    checkActionVisualization.setSelected(settings.get(ConfigTags.VisualizeActions));
    txtSutPath.setText(settings.get(ConfigTags.SUTConnectorValue));
    sutProcessesField.setText(settings.get(ConfigTags.SUTProcesses));
    comboBoxProtocol.setSelectedItem(settings.get(ConfigTags.ProtocolClass).split("/")[0]);
    spnNumSequences.setValue(settings.get(ConfigTags.Sequences));
    spnSequenceLength.setValue(settings.get(ConfigTags.SequenceLength));
    compileCheckBox.setSelected(settings.get(ConfigTags.AlwaysCompile));
    applicationNameField.setText(settings.get(ConfigTags.ApplicationName));
    applicationVersionField.setText(settings.get(ConfigTags.ApplicationVersion));
    overrideWebDriverDisplayScaleField.setText(settings.get(ConfigTags.OverrideWebDriverDisplayScale));
  }

  /**
   * Retrieve information from the JPanelGeneral GUI.
   *
   * @param settings reference to the object where the settings will be stored.
   */
  @Override
  public void extractInformation(final Settings settings) {
    settings.set(ConfigTags.SUTConnector, (String) cboxSUTconnector.getSelectedItem());
    settings.set(ConfigTags.SUTConnectorValue, txtSutPath.getText());
    settings.set(ConfigTags.SUTProcesses, sutProcessesField.getText());
    //settings.set(ConfigTags.StopGenerationOnFault, checkStopOnFault.isSelected());
    settings.set(ConfigTags.VisualizeActions, checkActionVisualization.isSelected());
    settings.set(ConfigTags.Sequences, (Integer) spnNumSequences.getValue());
    settings.set(ConfigTags.SequenceLength, (Integer) spnSequenceLength.getValue());
    settings.set(ConfigTags.AlwaysCompile, compileCheckBox.isSelected());
    settings.set(ConfigTags.ApplicationName, applicationNameField.getText());
    settings.set(ConfigTags.ApplicationVersion, applicationVersionField.getText());
    settings.set(ConfigTags.OverrideWebDriverDisplayScale, overrideWebDriverDisplayScaleField.getText());
  }

  public class MyItemListener extends Observable implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent e) {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        setChanged();
        notifyObservers(e.getItem());
      }
    }
  }
}
