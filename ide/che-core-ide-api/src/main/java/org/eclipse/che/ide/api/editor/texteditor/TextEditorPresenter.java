/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.api.editor.texteditor;

import com.google.common.base.Optional;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.callback.CallbackPromiseHelper;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.debug.BreakpointManager;
import org.eclipse.che.ide.api.debug.BreakpointRenderer;
import org.eclipse.che.ide.api.debug.BreakpointRendererFactory;
import org.eclipse.che.ide.api.debug.HasBreakpointRenderer;
import org.eclipse.che.ide.api.dialogs.CancelCallback;
import org.eclipse.che.ide.api.dialogs.ConfirmCallback;
import org.eclipse.che.ide.api.dialogs.DialogFactory;
import org.eclipse.che.ide.api.editor.AbstractEditorPresenter;
import org.eclipse.che.ide.api.editor.EditorAgent.OpenEditorCallback;
import org.eclipse.che.ide.api.editor.EditorInput;
import org.eclipse.che.ide.api.editor.EditorLocalizationConstants;
import org.eclipse.che.ide.api.editor.EditorWithAutoSave;
import org.eclipse.che.ide.api.editor.EditorWithErrors;
import org.eclipse.che.ide.api.editor.codeassist.CodeAssistProcessor;
import org.eclipse.che.ide.api.editor.codeassist.CodeAssistantFactory;
import org.eclipse.che.ide.api.editor.codeassist.CompletionsSource;
import org.eclipse.che.ide.api.editor.document.Document;
import org.eclipse.che.ide.api.editor.document.DocumentStorage;
import org.eclipse.che.ide.api.editor.document.DocumentStorage.DocumentCallback;
import org.eclipse.che.ide.api.editor.editorconfig.EditorUpdateAction;
import org.eclipse.che.ide.api.editor.editorconfig.TextEditorConfiguration;
import org.eclipse.che.ide.api.editor.events.CompletionRequestEvent;
import org.eclipse.che.ide.api.editor.events.DocumentReadyEvent;
import org.eclipse.che.ide.api.editor.events.GutterClickEvent;
import org.eclipse.che.ide.api.editor.events.GutterClickHandler;
import org.eclipse.che.ide.api.editor.filetype.FileTypeIdentifier;
import org.eclipse.che.ide.api.editor.formatter.ContentFormatter;
import org.eclipse.che.ide.api.editor.gutter.Gutters;
import org.eclipse.che.ide.api.editor.gutter.HasGutter;
import org.eclipse.che.ide.api.editor.keymap.KeyBinding;
import org.eclipse.che.ide.api.editor.keymap.KeyBindingAction;
import org.eclipse.che.ide.api.editor.position.PositionConverter;
import org.eclipse.che.ide.api.editor.quickfix.QuickAssistAssistant;
import org.eclipse.che.ide.api.editor.quickfix.QuickAssistProcessor;
import org.eclipse.che.ide.api.editor.quickfix.QuickAssistantFactory;
import org.eclipse.che.ide.api.editor.reconciler.Reconciler;
import org.eclipse.che.ide.api.editor.reconciler.ReconcilerWithAutoSave;
import org.eclipse.che.ide.api.editor.text.LinearRange;
import org.eclipse.che.ide.api.editor.text.TextPosition;
import org.eclipse.che.ide.api.editor.text.TextRange;
import org.eclipse.che.ide.api.editor.texteditor.EditorWidget.WidgetInitializedCallback;
import org.eclipse.che.ide.api.editor.texteditor.TextEditorPartView.Delegate;
import org.eclipse.che.ide.api.event.FileContentUpdateEvent;
import org.eclipse.che.ide.api.event.FileContentUpdateHandler;
import org.eclipse.che.ide.api.event.FileEvent;
import org.eclipse.che.ide.api.event.FileEventHandler;
import org.eclipse.che.ide.api.hotkeys.HasHotKeyItems;
import org.eclipse.che.ide.api.hotkeys.HotKeyItem;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.api.resources.File;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.ResourceChangedEvent;
import org.eclipse.che.ide.api.resources.ResourceDelta;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.api.selection.Selection;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.ui.loaders.request.LoaderFactory;
import org.vectomatic.dom.svg.ui.SVGResource;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.NOT_EMERGE_MODE;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.FAIL;
import static org.eclipse.che.ide.api.resources.ResourceDelta.ADDED;
import static org.eclipse.che.ide.api.resources.ResourceDelta.DERIVED;
import static org.eclipse.che.ide.api.resources.ResourceDelta.MOVED_FROM;
import static org.eclipse.che.ide.api.resources.ResourceDelta.MOVED_TO;
import static org.eclipse.che.ide.api.resources.ResourceDelta.REMOVED;
import static org.eclipse.che.ide.api.resources.ResourceDelta.UPDATED;

/**
 * Presenter part for the editor implementations.
 *
 * @deprecated use {@link TextEditor} instead
 */
@Deprecated
public class TextEditorPresenter<T extends EditorWidget> extends AbstractEditorPresenter implements TextEditor,
                                                                                                    FileEventHandler,
                                                                                                    UndoableEditor,
                                                                                                    HasBreakpointRenderer,
                                                                                                    HasReadOnlyProperty,
                                                                                                    HandlesTextOperations,
                                                                                                    EditorWithAutoSave,
                                                                                                    EditorWithErrors,
                                                                                                    HasHotKeyItems,
                                                                                                    Delegate {
    /** File type used when we have no idea of the actual content type. */
    public static final String DEFAULT_CONTENT_TYPE = "text/plain";

    private static final String TOGGLE_LINE_BREAKPOINT = "Toggle line breakpoint";

    private final CodeAssistantFactory        codeAssistantFactory;
    private final BreakpointManager           breakpointManager;
    private final BreakpointRendererFactory   breakpointRendererFactory;
    private final DialogFactory               dialogFactory;
    private final DocumentStorage             documentStorage;
    private final EditorLocalizationConstants constant;
    private final EditorWidgetFactory<T>      editorWidgetFactory;
    private final EditorModule             editorModule;
    private final TextEditorPartView          editorView;
    private final EventBus                    generalEventBus;
    private final FileTypeIdentifier          fileTypeIdentifier;
    private final QuickAssistantFactory       quickAssistantFactory;
    private final WorkspaceAgent              workspaceAgent;
    private final NotificationManager         notificationManager;
    private final AppContext                  appContext;

    /** The editor handle for this editor. */
    private final EditorHandle handle;

    private HasKeyBindings           keyBindingsManager;
    private List<EditorUpdateAction> updateActions;
    private TextEditorConfiguration  configuration;
    private EditorWidget             editorWidget;
    private Document                 document;
    private CursorModelWithHandler   cursorModel;
    private QuickAssistAssistant     quickAssistant;
    private LoaderFactory            loaderFactory;
    /** The editor's error state. */
    private EditorState              errorState;
    private boolean                  delayedFocus;
    private boolean                  isFocused;
    private BreakpointRenderer       breakpointRenderer;
    private List<String>             fileTypes;
    private TextPosition             cursorPosition;
    private HandlerRegistration      resourceChangeHandler;
    private TextEditorInit<T> editorInit;

    @AssistedInject
    public TextEditorPresenter(final CodeAssistantFactory codeAssistantFactory,
                               final BreakpointManager breakpointManager,
                               final BreakpointRendererFactory breakpointRendererFactory,
                               final DialogFactory dialogFactory,
                               final DocumentStorage documentStorage,
                               final EditorLocalizationConstants constant,
                               @Assisted final EditorWidgetFactory<T> editorWidgetFactory,
                               final EditorModule editorModule,
                               final TextEditorPartView editorView,
                               final EventBus eventBus,
                               final FileTypeIdentifier fileTypeIdentifier,
                               final QuickAssistantFactory quickAssistantFactory,
                               final WorkspaceAgent workspaceAgent,
                               final NotificationManager notificationManager,
                               final AppContext appContext
                              ) {
        this.codeAssistantFactory = codeAssistantFactory;
        this.breakpointManager = breakpointManager;
        this.breakpointRendererFactory = breakpointRendererFactory;
        this.dialogFactory = dialogFactory;
        this.documentStorage = documentStorage;
        this.constant = constant;
        this.editorWidgetFactory = editorWidgetFactory;
        this.editorModule = editorModule;
        this.editorView = editorView;
        this.generalEventBus = eventBus;
        this.fileTypeIdentifier = fileTypeIdentifier;
        this.quickAssistantFactory = quickAssistantFactory;
        this.workspaceAgent = workspaceAgent;
        this.notificationManager = notificationManager;
        this.appContext = appContext;

        keyBindingsManager = new TemporaryKeyBindingsManager();
        handle = new EditorHandle() {
        };

        this.editorView.setDelegate(this);
        eventBus.addHandler(FileEvent.TYPE, this);
    }

    @Override
    protected void initializeEditor(final OpenEditorCallback callback) {
        QuickAssistProcessor processor = configuration.getQuickAssistProcessor();
        if (quickAssistantFactory != null && processor != null) {
            quickAssistant = quickAssistantFactory.createQuickAssistant(this);
            quickAssistant.setQuickAssistProcessor(processor);
        }


        Promise<Document> documentPromice = CallbackPromiseHelper.createFromCallback(new CallbackPromiseHelper.Call<Document, Throwable>() {
            @Override
            public void makeCall(Callback<Document, Throwable> callback) {

            }
        });
        editorInit = new TextEditorInit<>(configuration,
                                          generalEventBus,
                                          this.codeAssistantFactory,
                                          this.quickAssistant,
                                          this);
        editorInit.init();

        if (editorModule.isError()) {
            displayErrorPanel(constant.editorInitErrorMessage());
            return;
        }
        final boolean moduleReady = editorModule.isReady();
        EditorInitCallback<T> dualCallback = new EditorInitCallback<T>(moduleReady, loaderFactory, constant) {
            @Override
            public void onReady(final String content) {
                createEditor(content);
            }

            @Override
            public void onError() {
                displayErrorPanel(constant.editorInitErrorMessage());
                callback.onInitializationFailed();
            }

            @Override
            public void onFileError() {
                displayErrorPanel(constant.editorFileErrorMessage());
                callback.onInitializationFailed();
            }
        };
        documentStorage.getDocument(input.getFile(), dualCallback);
        if (!moduleReady) {
            editorModule.waitReady(dualCallback);
        }
    }

    /**
     * Show the quick assist assistant.
     */
    public void showQuickAssist() {
        if (quickAssistant == null) {
            return;
        }
        PositionConverter positionConverter = getPositionConverter();
        if (positionConverter != null) {
            TextPosition cursor = getCursorPosition();
            PositionConverter.PixelCoordinates pixelPos = positionConverter.textToPixel(cursor);
            quickAssistant.showPossibleQuickAssists(getCursorModel().getCursorPosition().getOffset(),
                                                    pixelPos.getX(),
                                                    pixelPos.getY());
        }
    }

    private void createEditor(final String content) {
        this.fileTypes = detectFileType(getEditorInput().getFile());
        editorWidgetFactory.createEditorWidget(fileTypes, new EditorWidgetInitializedCallback(content));
    }

    private void setupEventHandlers() {
        this.editorWidget.addChangeHandler(new ChangeHandler() {
            @Override
            public void onChange(final ChangeEvent event) {
                handleDocumentChanged();
            }
        });
        this.editorWidget.addGutterClickHandler(new GutterClickHandler() {
            @Override
            public void onGutterClick(final GutterClickEvent event) {
                if (Gutters.BREAKPOINTS_GUTTER.equals(event.getGutterId())
                    || Gutters.LINE_NUMBERS_GUTTER.equals(event.getGutterId())) {
                    breakpointManager.changeBreakpointState(event.getLineNumber());
                }
            }
        });
        this.editorWidget.addKeyBinding(new KeyBinding(true, false, false, false, KeyCodes.KEY_F8, new KeyBindingAction() {
            @Override
            public void action() {
                int currentLine = editorWidget.getDocument().getCursorPosition().getLine();
                breakpointManager.changeBreakpointState(currentLine);
            }
        }), TOGGLE_LINE_BREAKPOINT);
    }

    private void setupFileContentUpdateHandler() {

        resourceChangeHandler =
                generalEventBus.addHandler(ResourceChangedEvent.getType(), new ResourceChangedEvent.ResourceChangedHandler() {
                    @Override
                    public void onResourceChanged(ResourceChangedEvent event) {
                        final ResourceDelta delta = event.getDelta();

                        switch (delta.getKind()) {
                            case ADDED:
                                onResourceCreated(delta);
                                break;
                            case REMOVED:
                                onResourceRemoved(delta);
                                break;
                            case UPDATED:
                                onResourceUpdated(delta);
                        }
                    }
                });

        this.generalEventBus.addHandler(FileContentUpdateEvent.TYPE, new FileContentUpdateHandler() {
            @Override
            public void onFileContentUpdate(final FileContentUpdateEvent event) {
                if (event.getFilePath() != null && Path.valueOf(event.getFilePath()).equals(document.getFile().getLocation())) {
                    updateContent();
                }
            }
        });
    }

    private void onResourceCreated(ResourceDelta delta) {
        if ((delta.getFlags() & (MOVED_FROM | MOVED_TO)) == 0) {
            return;
        }

        //file moved directly
        if (delta.getFromPath().equals(document.getFile().getLocation())) {
            final Resource resource = delta.getResource();
            final Path movedFrom = delta.getFromPath();

            if (document.getFile().getLocation().equals(movedFrom)) {
                document.setFile((File)resource);
                input.setFile((File)resource);
            }

            updateContent();
        } else if (delta.getFromPath().isPrefixOf(document.getFile().getLocation())) { //directory where file moved
            final Path relPath = document.getFile().getLocation().removeFirstSegments(delta.getFromPath().segmentCount());
            final Path newPath = delta.getToPath().append(relPath);

            appContext.getWorkspaceRoot().getFile(newPath).then(new Operation<Optional<File>>() {
                @Override
                public void apply(Optional<File> file) throws OperationException {
                    if (file.isPresent()) {
                        document.setFile(file.get());
                        input.setFile(file.get());

                        updateContent();
                    }
                }
            });
        }


    }

    private void onResourceRemoved(ResourceDelta delta) {
        if ((delta.getFlags() & DERIVED) == 0) {
            return;
        }

        final Resource resource = delta.getResource();

        if (resource.isFile() && document.getFile().getLocation().equals(resource.getLocation())) {
            if (resourceChangeHandler != null) {
                resourceChangeHandler.removeHandler();
                resourceChangeHandler = null;
            }
            close(false);
        }
    }

    private void onResourceUpdated(ResourceDelta delta) {
        if ((delta.getFlags() & DERIVED) == 0) {
            return;
        }

        if (delta.getResource().isFile() && document.getFile().getLocation().equals(delta.getResource().getLocation())) {
            updateContent();
        }
    }

    private void updateContent() {
        /* -save current cursor and (ideally) viewport
         * -set editor content which is also expected to
         *     -reset dirty flag
         *     -clear history
         * -restore current cursor position
         */
        final TextPosition currentCursor = getCursorPosition();
        this.documentStorage.getDocument(document.getFile(), new DocumentCallback() {

            @Override
            public void onDocumentReceived(final String content) {
                editorWidget.setValue(content, new ContentInitializedHandler() {
                    @Override
                    public void onContentInitialized() {
                        document.setCursorPosition(currentCursor);
                    }
                });
            }

            @Override
            public void onDocumentLoadFailure(final Throwable caught) {
                displayErrorPanel(constant.editorFileErrorMessage());
            }
        });
    }

    private void displayErrorPanel(final String message) {
        this.editorView.showPlaceHolder(new Label(message));
    }

    private void handleDocumentChanged() {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            @Override
            public void execute() {
                updateDirtyState(editorWidget.isDirty());
            }
        });
    }

    @Override
    public void storeState() {
        cursorPosition = getCursorPosition();
    }

    @Override
    public void restoreState() {
        if (cursorPosition != null) {
            setFocus();

            getDocument().setCursorPosition(cursorPosition);
        }
    }

    @Override
    public void close(final boolean save) {
        this.documentStorage.documentClosed(this.document);
        editorInit.uninstall();
        workspaceAgent.removePart(this);
    }

    @Inject
    public void injectAsyncLoader(final LoaderFactory loaderFactory) {
        this.loaderFactory = loaderFactory;
    }

    @Override
    public boolean isEditable() {
        return false;
    }

    @Override
    public void doRevertToSaved() {
        // do nothing
    }

    @NotNull
    protected Widget getWidget() {
        return this.editorView.asWidget();
    }

    @Override
    public void go(final AcceptsOneWidget container) {
        container.setWidget(getWidget());
    }

    @Override
    public String getTitleToolTip() {
        return null;
    }

    @Override
    public void onClose(@NotNull final AsyncCallback<Void> callback) {
        if (isDirty()) {
            dialogFactory.createConfirmDialog(
                    constant.askWindowCloseTitle(),
                    constant.askWindowSaveChangesMessage(getEditorInput().getName()),
                    new ConfirmCallback() {
                        @Override
                        public void accepted() {
                            doSave();
                            handleClose();
                            callback.onSuccess(null);
                        }
                    },
                    new CancelCallback() {
                        @Override
                        public void cancelled() {
                            handleClose();
                            callback.onSuccess(null);
                        }
                    }).show();
        } else {
            handleClose();
            callback.onSuccess(null);
        }
    }

    @Override
    protected void handleClose() {
        if (resourceChangeHandler != null) {
            resourceChangeHandler.removeHandler();
            resourceChangeHandler = null;
        }

        super.handleClose();
    }

    @Override
    public TextEditorPartView getView() {
        return this.editorView;
    }

    @Override
    public void activate() {
        if (editorWidget != null) {
            editorWidget.refresh();
            editorWidget.setFocus();
            setSelection(new Selection<>(input.getFile()));
        } else {
            this.delayedFocus = true;
        }
    }

    @Override
    public void onFileOperation(final FileEvent event) {
        if (event.getOperationType() != FileEvent.FileOperation.CLOSE) {
            return;
        }

        if (input.getFile().equals(event.getFile())) {
            close(false);
        }
    }

    @Override
    public void initialize(@NotNull final TextEditorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public TextEditorConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public SVGResource getTitleImage() {
        return input.getSVGResource();
    }

    @NotNull
    @Override
    public String getTitle() {
        return input.getFile().getDisplayName();
    }

    @Override
    public void doSave() {
        doSave(new AsyncCallback<EditorInput>() {
            @Override
            public void onSuccess(final EditorInput result) {
                // do nothing
            }

            @Override
            public void onFailure(final Throwable caught) {
                // do nothing
            }
        });
    }

    @Override
    public void doSave(final AsyncCallback<EditorInput> callback) {

        this.documentStorage.saveDocument(getEditorInput(), this.document, false, new AsyncCallback<EditorInput>() {
            @Override
            public void onSuccess(EditorInput editorInput) {
                updateDirtyState(false);
                editorWidget.markClean();
                afterSave();
                if (callback != null) {
                    callback.onSuccess(editorInput);
                }
            }

            @Override
            public void onFailure(Throwable caught) {
                notificationManager.notify(constant.failedToUpdateContentOfFiles(), caught.getMessage(), FAIL, NOT_EMERGE_MODE);
                if (callback != null) {
                    callback.onFailure(caught);
                }
            }
        });
    }

    /** Override this method for handling after save actions. */
    protected void afterSave() {
    }

    @Override
    public void doSaveAs() {
    }

    @Override
    public HandlesUndoRedo getUndoRedo() {
        if (this.editorWidget != null) {
            return this.editorWidget.getUndoRedo();
        } else {
            return null;
        }
    }

    @Override
    public EditorState getErrorState() {
        return this.errorState;
    }

    @Override
    public void setErrorState(final EditorState errorState) {
        this.errorState = errorState;
        firePropertyChange(ERROR_STATE);
    }

    @Override
    public BreakpointRenderer getBreakpointRenderer() {
        if (this.breakpointRenderer == null && this.editorWidget != null && this instanceof HasGutter) {
            this.breakpointRenderer = this.breakpointRendererFactory.create(((HasGutter)this).getGutter(),
                                                                            this.editorWidget.getLineStyler(),
                                                                            this.document);
        }
        return this.breakpointRenderer;
    }

    @Override
    public Document getDocument() {
        return this.document;
    }

    @Override
    public String getContentType() {
        // Before the editor content is ready, the content type is not defined
        if (this.fileTypes == null || this.fileTypes.isEmpty()) {
            return null;
        } else {
            return this.fileTypes.get(0);
        }
    }

    @Override
    public TextRange getSelectedTextRange() {
        return getDocument().getSelectedTextRange();
    }

    @Override
    public LinearRange getSelectedLinearRange() {
        return getDocument().getSelectedLinearRange();
    }

    @Override
    public void showMessage(final String message) {
        this.editorWidget.showMessage(message);
    }

    @Override
    public TextPosition getCursorPosition() {
        return getDocument().getCursorPosition();
    }

    @Override
    public int getCursorOffset() {
        final TextPosition textPosition = getDocument().getCursorPosition();
        return getDocument().getIndexFromPosition(textPosition);
    }

    @Override
    public void refreshEditor() {
        if (this.updateActions != null) {
            for (final EditorUpdateAction action : this.updateActions) {
                action.doRefresh();
            }
        }
    }

    @Override
    public void addEditorUpdateAction(final EditorUpdateAction action) {
        if (action == null) {
            return;
        }
        if (this.updateActions == null) {
            this.updateActions = new ArrayList<>();
        }
        this.updateActions.add(action);
    }

    @Override
    public void addKeybinding(final KeyBinding keyBinding) {
        // the actual HasKeyBindings object can change, so use indirection
        getHasKeybindings().addKeyBinding(keyBinding);
    }

    private List<String> detectFileType(final VirtualFile file) {
        final List<String> result = new ArrayList<>();
        if (file != null) {
            // use the identification patterns
            final List<String> types = this.fileTypeIdentifier.identifyType(file);
            if (types != null && !types.isEmpty()) {
                result.addAll(types);
            }
        }

        // ultimate fallback - can't make more generic for text
        result.add(DEFAULT_CONTENT_TYPE);

        return result;
    }

    public HasTextMarkers getHasTextMarkers() {
        if (this.editorWidget != null) {
            return this.editorWidget;
        } else {
            return null;
        }
    }

    public HasKeyBindings getHasKeybindings() {
        return this.keyBindingsManager;
    }

    @Override
    public CursorModelWithHandler getCursorModel() {
        return this.cursorModel;
    }

    @Override
    public PositionConverter getPositionConverter() {
        return this.editorWidget.getPositionConverter();
    }

    public void showCompletionProposals(final CompletionsSource source) {
        this.editorView.showCompletionProposals(this.editorWidget, source);
    }

    public boolean isCompletionProposalsShowing() {
        return editorWidget.isCompletionProposalsShowing();
    }

    public void showCompletionProposals() {
        this.editorView.showCompletionProposals(this.editorWidget);
    }

    public EditorHandle getEditorHandle() {
        return this.handle;
    }

    private void switchHasKeybinding() {
        final HasKeyBindings current = getHasKeybindings();
        if (!(current instanceof TemporaryKeyBindingsManager)) {
            return;
        }
        // change the key binding instance and add all bindings to the new one
        this.keyBindingsManager = this.editorWidget;
        final List<KeyBinding> bindings = ((TemporaryKeyBindingsManager)current).getbindings();
        for (final KeyBinding binding : bindings) {
            this.keyBindingsManager.addKeyBinding(binding);
        }
    }

    @Override
    public List<HotKeyItem> getHotKeys() {
        return editorWidget.getHotKeys();
    }

    @Override
    public void onResize() {
        if (this.editorWidget != null) {
            this.editorWidget.onResize();
        }
    }

    @Override
    public void editorLostFocus() {
        this.editorView.updateInfoPanelUnfocused(this.document.getLineCount());
        this.isFocused = false;
    }

    @Override
    public void editorGotFocus() {
        this.isFocused = true;
        this.editorView.updateInfoPanelPosition(this.document.getCursorPosition());
    }

    @Override
    public void editorCursorPositionChanged() {
        this.editorView.updateInfoPanelPosition(this.document.getCursorPosition());
    }

    @Override
    public boolean canDoOperation(final int operation) {
        if (TextEditorOperations.CODEASSIST_PROPOSALS == operation) {
            Map<String, CodeAssistProcessor> contentAssistProcessors = getConfiguration().getContentAssistantProcessors();
            if (contentAssistProcessors != null && !contentAssistProcessors.isEmpty()) {
                return true;
            }
        }
        if (TextEditorOperations.FORMAT == operation) {
            if (getConfiguration().getContentFormatter() != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void doOperation(final int operation) {
        switch (operation) {
            case TextEditorOperations.CODEASSIST_PROPOSALS:
                if (this.document != null) {
                    this.document.getDocumentHandle().getDocEventBus().fireEvent(new CompletionRequestEvent());
                }
                break;
            case TextEditorOperations.FORMAT:
                ContentFormatter formatter = getConfiguration().getContentFormatter();
                if (this.document != null && formatter != null) {
                    formatter.format(getDocument());
                }
                break;
            default:
                throw new UnsupportedOperationException("Operation code: " + operation + " is not supported!");
        }
    }

    @Override
    public boolean isReadOnly() {
        return this.editorWidget.isReadOnly();
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        this.editorWidget.setReadOnly(readOnly);
    }

    @Override
    public EditorWidget getEditorWidget() {
        return this.editorWidget;
    }

    public boolean isFocused() {
        return this.isFocused;
    }

    @Override
    public void setFocus() {
        editorWidget.setFocus();
    }

    @Override
    public boolean isAutoSaveEnabled() {
        ReconcilerWithAutoSave autoSave = getAutoSave();
        return autoSave != null && autoSave.isAutoSaveEnabled();
    }

    private ReconcilerWithAutoSave getAutoSave() {
        Reconciler reconciler = getConfiguration().getReconciler();

        if (reconciler != null && reconciler instanceof ReconcilerWithAutoSave) {
            return ((ReconcilerWithAutoSave)reconciler);
        }
        return null;
    }

    @Override
    public void enableAutoSave() {
        ReconcilerWithAutoSave autoSave = getAutoSave();
        if (autoSave != null) {
            autoSave.enableAutoSave();
        }
    }

    @Override
    public void disableAutoSave() {
        ReconcilerWithAutoSave autoSave = getAutoSave();
        if (autoSave != null) {
            autoSave.disableAutoSave();
        }
    }

    private class EditorWidgetInitializedCallback implements WidgetInitializedCallback {
        private final String content;

        private boolean isInitialized;

        private EditorWidgetInitializedCallback(String content) {
            this.content = content;
        }

        @Override
        public void initialized(EditorWidget widget) {
            editorWidget = widget;
            // finish editor initialization
            editorView.setEditorWidget(editorWidget);

            document = editorWidget.getDocument();
            document.setFile(input.getFile());
            cursorModel = new TextEditorCursorModel(document);

            editorWidget.setTabSize(configuration.getTabWidth());

            // initialize info panel
            editorView.initInfoPanel(editorWidget.getMode(),
                                     editorWidget.getKeymap(),
                                     document.getLineCount(),
                                     configuration.getTabWidth());

            //TODO: delayed activation
            // handle delayed focus (initialization editor widget)
            // should also check if I am visible, but how ?
            if (delayedFocus) {
                editorWidget.refresh();
                editorWidget.setFocus();
                setSelection(new Selection<>(input.getFile()));
                delayedFocus = false;
            }

            // delayed keybindings creation ?
            switchHasKeybinding();

            editorWidget.setValue(content, new ContentInitializedHandler() {
                @Override
                public void onContentInitialized() {
                    if (isInitialized) {
                        return;
                    }
                    generalEventBus.fireEvent(new DocumentReadyEvent(getEditorHandle(), document));
                    firePropertyChange(PROP_INPUT);
                    setupEventHandlers();
                    setupFileContentUpdateHandler();

                    isInitialized = true;
                }
            });
        }
    }
}
