package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPContainingClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPPropertyTypeElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.tree.DPClassNode;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.tree.DPPropertyNode;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.virtual.DynamicPropertyVirtual;
//import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.toolPanel.DynamicToolWindowUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2008
 */
public class DynamicToolWindowWrapper {

  public static final String DYNAMIC_TOOLWINDOW_ID = GroovyBundle.message("dynamic.tool.window.id");
  //  private ToolWindow myDynamicToolWindow;
  //  private static final Project myPro+ject;
  private static JPanel myTreeTablePanel;
  private static JPanel myBigPanel;

  private static DynamicTreeViewState myState = new DynamicTreeViewState();

  private static ListTreeTableModelOnColumns myTreeTableModel;
  private static String[] myColumnNames = {
      "Class and properties",
      "Type of property"
  };
  private static TreeTable myTreeTable;

  private static final Key<DynamicTreeViewState> DYNAMIC_TOOLWINDOW_STATE_KEY = Key.create("DYNAMIC_TOOLWINDOW_STATE");

//  public DynamicToolWindowWrapper(ToolWindow dynamicToolWindow) {
//    myDynamicToolWindow = dynamicToolWindow;
//  }

  private static boolean doesTreeTableInit() {
    return myBigPanel != null && myTreeTableModel != null && myTreeTablePanel != null;
  }

  public static TreeTable getTreeTable(ToolWindow window, Project project) {
    if (!doesTreeTableInit()) {
      reconfigureWindow(project, window);
    }

    return returnTreeTable();
  }

  public static void configureWindow(final Project project, ToolWindow window) {
    reconfigureWindow(project, window);
  }

  private static void reconfigureWindow(final Project project, ToolWindow window) {
    window.setTitle(GroovyBundle.message("toolwindow.dynamic.properties"));
    window.setToHideOnEmptyContent(true);

    DynamicPropertiesManager.getInstance(project).addDynamicChangeListener(new DynamicPropertyChangeListener() {
      public void dynamicPropertyChange() {
        storeState(project);
        rebuildTreePanel(project);
        restoreState(project);
      }
    });

    Disposer.register(window.getContentManager(), new Disposable() {
      public void dispose() {
        storeState(project);
      }
    });

    buildBigPanel(project);
    window.getComponent().add(getContentPane(project));
  }

  private static JPanel buildBigPanel(final Project project) {
    myBigPanel = new JPanel(new BorderLayout());
    myBigPanel.setBackground(UIUtil.getFieldForegroundColor());

    final DynamicFilterComponent filter = new DynamicFilterComponent(project, GroovyBundle.message("dynamic.toolwindow.property.fiter"), 10);
    filter.setBackground(UIUtil.getLabelBackground());

    myBigPanel.add(new Label(GroovyBundle.message("dynamic.toolwindow.search.property")), BorderLayout.NORTH);
    myBigPanel.add(filter, BorderLayout.NORTH);

    myTreeTablePanel = new JPanel(new BorderLayout());
    rebuildTreePanel(project);

    myBigPanel.add(myTreeTablePanel);
    myBigPanel.setPreferredSize(new Dimension(200, myBigPanel.getHeight()));

    myBigPanel.revalidate();
    return myBigPanel;
  }

  private static void rebuildTreePanel(Project project) {
//    storeState(project);
//    if (!isDynamicToolWindowShowing()) return;

    DefaultMutableTreeNode myRootNode = new DefaultMutableTreeNode();
    buildTree(project, myRootNode);

    rebuildTreeView(project, myRootNode, false);

//    restoreState(project);
  }

  private static void rebuildTreeView(Project project, DefaultMutableTreeNode root, boolean expandAll) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
//    if (!isDynamicToolWindowShowing()) return;

//    storeState();
    myTreeTablePanel.removeAll();

    final JScrollPane treeTable = createTable(project, root);

    if (expandAll) {
      TreeUtil.expandAll(myTreeTable.getTree());
    }

    myTreeTablePanel.add(treeTable);
  }

  private static DefaultMutableTreeNode buildTree(Project project, DefaultMutableTreeNode rootNode) {
    final Module module = getModule(project);
    if (module == null) return new DefaultMutableTreeNode();

    final Set<String> containingClasses = DynamicPropertiesManager.getInstance(project).getAllContainingClasses(module.getName());

    DefaultMutableTreeNode containingClassNode;
    for (String containingClassName : containingClasses) {
      containingClassNode = new DefaultMutableTreeNode(new DPClassNode(new DPContainingClassElement(containingClassName)));

      final String[] properties = DynamicPropertiesManager.getInstance(project).findDynamicPropertiesOfClass(module.getName(), containingClassName);

      if (properties.length == 0) continue;

      DefaultMutableTreeNode propertyTreeNode;
      for (String propertyName : properties) {
        final String propertyType = DynamicPropertiesManager.getInstance(project).findDynamicPropertyType(module.getName(), containingClassName, propertyName);
        //TODO: simplify Hierarchy
        propertyTreeNode = new DefaultMutableTreeNode(new DPPropertyNode(new DPPropertyElement(new DynamicPropertyVirtual(propertyName, containingClassName, module.getName(), propertyType))));
        containingClassNode.add(propertyTreeNode);
      }

      rootNode.add(containingClassNode);
    }
    return rootNode;
  }

  private static JScrollPane createTable(final Project project, MutableTreeNode myTreeRoot) {
    ColumnInfo[] columnInfos = {new ClassColumnInfo(myColumnNames[0]), new PropertyTypeColumnInfo(myColumnNames[1])};

    myTreeTableModel = new ListTreeTableModelOnColumns(myTreeRoot, columnInfos);
    myTreeTable = new TreeTable(myTreeTableModel);
    myTreeTable.setRootVisible(false);

    myTreeTable.registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(ActionEvent event) {
            storeState(project);
            deleteRow(project);
            restoreState(project);
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
        JComponent.WHEN_FOCUSED
    );

    // todo use "myTreeTable.setAutoCreateRowSorter(true);" since 1.6

    myTreeTable.getTree().setShowsRootHandles(true);
    myTreeTable.getTableHeader().setReorderingAllowed(false);

    myTreeTable.setTreeCellRenderer(new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        value = ((DefaultMutableTreeNode) value).getUserObject();

        setPaintFocusBorder(false);

        if (value != null) {

          if (value instanceof DPClassNode) {
            append(((DPClassNode) value).getElement().getContainingClassName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

          } else if (value instanceof DPPropertyNode) {
            final DPPropertyNode propertyElement = (DPPropertyNode) value;
            final String substringToHighlight = propertyElement.getElement().getHightlightedText();
            final String propertyName = propertyElement.getElement().getPropertyName();

            if (substringToHighlight != null) {
              final int begin = propertyName.indexOf(substringToHighlight);
              final String first = propertyName.substring(0, begin);
              append(first, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
              final TextAttributes textAttributes = TextAttributes.ERASE_MARKER;
//              textAttributes.setEffectColor(new Color(200, 200, 200));
              textAttributes.setBackgroundColor(UIUtil.getListSelectionBackground());
              append(substringToHighlight, SimpleTextAttributes.fromTextAttributes(textAttributes));
              append(propertyName.substring(first.length() + substringToHighlight.length()), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
            } else {
              append(propertyName, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
            }

          }/* else if (value instanceof DPPropertyTypeElement) {
            append(((DPPropertyTypeElement) value).getPropertyType(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
          }*/
        }
      }
    });

    myTreeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTreeTable.setPreferredScrollableViewportSize(new Dimension(300, myTreeTable.getRowHeight() * 10));
    myTreeTable.getColumn(myColumnNames[0]).setPreferredWidth(200);
    myTreeTable.getColumn(myColumnNames[1]).setPreferredWidth(160);

    myTreeTable.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            final Point point = e.getPoint();
            if (e.getClickCount() == 2 && myTreeTable.columnAtPoint(point) == 1) {
              myTreeTable.editCellAt(myTreeTable.rowAtPoint(point), myTreeTable.columnAtPoint(point), e);
            }
          }
        }
    );

    JScrollPane scrollpane = ScrollPaneFactory.createScrollPane(myTreeTable);

    scrollpane.setPreferredSize(new Dimension(600, 400));
    return scrollpane;
  }

  private static void deleteRow(Project project) {
    final int[] rows = myTreeTable.getSelectedRows();
//            for (int row : rows) {
    final TreePath selectionPath = myTreeTable.getTree().getAnchorSelectionPath();

    //class
    final TreePath parent = selectionPath.getParentPath();

    final Module module = getModule(project);
    if (parent.getParentPath() == null) {
      //selectionPath is class

      final Object containingClassRow = parent.getLastPathComponent();

      if (!(containingClassRow instanceof DefaultMutableTreeNode)) return;
      final Object containingClass = ((DefaultMutableTreeNode) containingClassRow).getUserObject();

      if (module == null) return;
      if (!(containingClass instanceof DPClassNode)) return;

      DynamicPropertiesManager.getInstance(project).removeDynamicPropertiesOfClass(module.getName(), ((DPClassNode) containingClass).getElement().getContainingClassName());
    } else {
      //selectionPath is property
      final Object containingClass = parent.getLastPathComponent();
      final Object property = selectionPath.getLastPathComponent();

      if (!(containingClass instanceof DefaultMutableTreeNode)) return;
      if (!(property instanceof DefaultMutableTreeNode)) return;

      final Object classElement = ((DefaultMutableTreeNode) containingClass).getUserObject();
      final Object propertyElement = ((DefaultMutableTreeNode) property).getUserObject();

      if (!(classElement instanceof DPClassNode)) return;
      if (!(propertyElement instanceof DPPropertyNode)) return;

      final String containingClassName = ((DPClassNode) classElement).getElement().getContainingClassName();
      final String propertyName = ((DPPropertyNode) propertyElement).getElement().getPropertyName();
      final String propertyType = ((DPPropertyNode) propertyElement).getElement().getPropertyType();

      DynamicPropertyVirtual dynamicProperty = new DynamicPropertyVirtual(propertyName, containingClassName, module.getName(), propertyType);

      DynamicPropertiesManager.getInstance(project).removeDynamicProperty(dynamicProperty);
    }
  }

  protected boolean isDynamicToolWindowShowing(Project project) {
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = windowManager.getToolWindow(DYNAMIC_TOOLWINDOW_ID);
    return toolWindow != null && toolWindow.isVisible();
  }

  private static JPanel getContentPane(Project project) {
    if (!doesTreeTableInit()) {
      buildBigPanel(project);
    }

    return myBigPanel;
  }

  private static TreeTable returnTreeTable() {
    return myTreeTable;
  }

  static class PropertyTypeColumnInfo extends ColumnInfo<DefaultMutableTreeNode, DPPropertyTypeElement> {
    public PropertyTypeColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DefaultMutableTreeNode defaultMutableTreeNode) {
      return true;
    }

//    public TableCellEditor getEditor(DefaultMutableTreeNode o) {
//      return super.getEditor(o);    //To change body of overridden methods use File | Settings | File Templates.
//    }

    public DPPropertyTypeElement valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();

      if (userObject instanceof DPPropertyNode)
        return ((DPPropertyNode) userObject).getElement().getPropertyTypeElement();

      return null;
    }
  }

  static class ClassColumnInfo extends ColumnInfo<DefaultMutableTreeNode, DPElement> {
    public ClassColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DefaultMutableTreeNode defaultMutableTreeNode) {
      return false;
    }

    public Class getColumnClass() {
      return TreeTableModel.class;
    }


    public DPElement valueOf(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof DPElement) return ((DPElement) userObject);

      return null;
    }
  }


  static class DynamicFilterComponent extends FilterComponent {
    private final Project myProject;

    public DynamicFilterComponent(Project project, @NonNls String propertyName, int historySize) {
      super(propertyName, historySize);
      myProject = project;
    }

    public void filter() {
      DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
      buildTree(myProject, rootNode);

      String filterText;

      DefaultMutableTreeNode newContainingClassNode;

      TreeNode containingClassNode;
      try {
        containingClassNode = rootNode.getFirstChild();
      } catch (Exception e) {
        return;
      }

      while (containingClassNode != null) {
        if (!(containingClassNode instanceof DefaultMutableTreeNode)) break;

        TreeNode child;
        try {
          child = ((DefaultMutableTreeNode) containingClassNode).getFirstChild();
        } catch (Exception e) {
          return;
        }

        DefaultMutableTreeNode newChild;
        while (child != null) {
          if (!(child instanceof DefaultMutableTreeNode)) break;

          final Object userObject = ((DefaultMutableTreeNode) child).getUserObject();
          if (!(userObject instanceof DPPropertyNode)) break;

          filterText = getFilter();
          if (filterText == null) break;

          final String propertyName = ((DPPropertyNode) userObject).getElement().getPropertyName();

          if (propertyName == null || "".equals(filterText)) break;

          newChild = ((DefaultMutableTreeNode) child).getNextSibling();

          if (!propertyName.contains(filterText)) {
            final TreeNode parent = child.getParent();

            if (!(parent instanceof DefaultMutableTreeNode)) break;

            ((DefaultMutableTreeNode) parent).remove(((DefaultMutableTreeNode) child));
          } else {
            ((DPPropertyNode) userObject).getElement().setHightlightedText(filterText);
          }

          child = newChild;
        }

        newContainingClassNode = ((DefaultMutableTreeNode) containingClassNode).getNextSibling();

        if (containingClassNode.getChildCount() == 0) {
          final TreeNode parent = containingClassNode.getParent();

          if (!(parent instanceof DefaultMutableTreeNode)) break;
          ((DefaultMutableTreeNode) parent).remove(((DefaultMutableTreeNode) containingClassNode));
        }

        containingClassNode = newContainingClassNode;
      }

      rebuildTreeView(myProject, rootNode, true);
    }
  }

  private static Module getModule(Project project) {
    //TODO
    final VirtualFile currentFile = FileEditorManagerEx.getInstanceEx(project).getCurrentFile();

    if (currentFile == null) {
      //TODO
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      return modules[0];
    }

    return ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(currentFile);
  }

  private static void storeState(Project project) {
    if (doesTreeTableInit()) {
      final Module module = getModule(project);

      if (module != null) {
        myState = getState();
        module.putUserData(DYNAMIC_TOOLWINDOW_STATE_KEY, myState);
      }
    }
  }

  private static void restoreState(Project project) {
    if (doesTreeTableInit()) {
      final Module module = getModule(project);

      if (module != null) {
        myState = module.getUserData(DYNAMIC_TOOLWINDOW_STATE_KEY);
      }

      TreeUtil.restoreExpandedPaths(myTreeTable.getTree(), myState.getExpandedElements());
    }
  }

  private static DynamicTreeViewState getState() {
    DynamicTreeViewState structureViewState = new DynamicTreeViewState();
    if (myTreeTable.getTree() != null) {
      structureViewState.setExpandedElements(getExpandedElements());
      structureViewState.setSelectedElements(getSelectedElements());
    }
    return structureViewState;
  }

  private static List<TreePath> getExpandedElements() {
    final JTree tree = myTreeTable.getTree();
    if (tree == null) return new ArrayList<TreePath>();
    return TreeUtil.collectExpandedPaths(tree);
  }

  private static List<TreePath> getSelectedElements() {
    final JTree tree = myTreeTable.getTree();
    TreePath[] selectionPaths = tree.getSelectionPaths();
    selectionPaths = selectionPaths != null ? selectionPaths : new TreePath[0];

    return Arrays.asList(selectionPaths);
  }

  public static ListTreeTableModelOnColumns getTreeTableModel(ToolWindow window, Project project) {
    if (!doesTreeTableInit()) {
      reconfigureWindow(project, window);
    }

    return returnTreeTableModel();
  }

  private static ListTreeTableModelOnColumns returnTreeTableModel() {
    return myTreeTableModel;
  }
}