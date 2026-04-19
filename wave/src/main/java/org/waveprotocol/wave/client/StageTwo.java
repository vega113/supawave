/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.waveprotocol.wave.client;

import org.waveprotocol.wave.model.util.Preconditions;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Event;
import org.waveprotocol.wave.client.debug.FragmentsDebugIndicator;

import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.account.impl.ProfileManagerImpl;
import org.waveprotocol.wave.client.common.util.AsyncHolder;
import org.waveprotocol.wave.client.common.util.ClientPercentEncoderDecoder;
import org.waveprotocol.wave.client.common.util.CountdownLatch;
import org.waveprotocol.wave.client.common.util.DateUtils;

import java.util.Date;

import org.waveprotocol.wave.client.concurrencycontrol.LiveChannelBinder;
import org.waveprotocol.wave.client.concurrencycontrol.MuxConnector;
import org.waveprotocol.wave.client.concurrencycontrol.ClientStatsRawFragmentsApplier;
import org.waveprotocol.wave.client.concurrencycontrol.WaveletOperationalizer;
import org.waveprotocol.wave.client.doodad.DoodadInstallers;
import org.waveprotocol.wave.client.doodad.attachment.AttachmentManagerProvider;
import org.waveprotocol.wave.client.doodad.attachment.ImageThumbnail;
import org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailWrapper;
import org.waveprotocol.wave.client.doodad.diff.DiffAnnotationHandler;
import org.waveprotocol.wave.client.doodad.diff.DiffDeleteRenderer;
import org.waveprotocol.wave.client.doodad.form.check.TaskAnnotationHandler;
import org.waveprotocol.wave.client.doodad.link.LinkAnnotationHandler;
import org.waveprotocol.wave.client.doodad.link.LinkAnnotationHandler.LinkAttributeAugmenter;
import org.waveprotocol.wave.client.doodad.mention.MentionAnnotationHandler;
import org.waveprotocol.wave.client.doodad.selection.SelectionAnnotationHandler;
import org.waveprotocol.wave.client.doodad.title.TitleAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.Registries;
import org.waveprotocol.wave.client.editor.content.misc.AnnotationPaint;
import org.waveprotocol.wave.client.editor.content.misc.StyleAnnotationHandler;
import org.waveprotocol.wave.client.events.ClientEvents;
import org.waveprotocol.wave.client.events.WaveSelectionEvent;
import org.waveprotocol.wave.client.render.ReductionBasedRenderer;
import org.waveprotocol.wave.client.render.RenderingRules;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.model.document.BlipReadStateMonitor;
import org.waveprotocol.wave.client.state.BlipReadStateMonitorImpl;
import org.waveprotocol.wave.client.state.ThreadReadStateMonitor;
import org.waveprotocol.wave.client.state.ThreadReadStateMonitorImpl;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.util.ClientFlags;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wave.LazyContentDocument;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wave.LocalSupplementedWaveImpl;
import org.waveprotocol.wave.client.wave.RegistriesHolder;
import org.waveprotocol.wave.client.wave.SimpleDiffDoc;
import org.waveprotocol.wave.client.wave.WaveDocuments;
import org.waveprotocol.box.webclient.client.Session;
import org.waveprotocol.wave.client.wavepanel.impl.NewBlipIndicatorPresenter;
import org.waveprotocol.wave.client.wavepanel.impl.diff.DiffController;
import org.waveprotocol.wave.client.wavepanel.impl.reader.Reader;
import org.waveprotocol.wave.client.wavepanel.render.BlipPager;
import org.waveprotocol.wave.client.wavepanel.render.BlipResourceCleaner;
import org.waveprotocol.wave.client.wavepanel.render.DocumentRegistries;
import org.waveprotocol.wave.client.wavepanel.render.FullDomRenderer;
import org.waveprotocol.wave.client.wavepanel.render.FullDomRenderer.DocRefRenderer;
import org.waveprotocol.wave.client.wavepanel.render.HtmlDomRenderer;
import org.waveprotocol.wave.client.wavepanel.render.InlineAnchorLiveRenderer;
import org.waveprotocol.wave.client.wavepanel.render.LiveConversationViewRenderer;
import org.waveprotocol.wave.client.wavepanel.render.PagingHandlerProxy;
import org.waveprotocol.wave.client.wavepanel.render.ReplyManager;
import org.waveprotocol.wave.client.wavepanel.render.ViewChannelFragmentRequester;
import org.waveprotocol.wave.client.wavepanel.render.ShallowBlipRenderer;
import org.waveprotocol.wave.client.wavepanel.render.UndercurrentShallowBlipRenderer;
import org.waveprotocol.wave.client.wavepanel.view.ModelIdMapper;
import org.waveprotocol.wave.client.wavepanel.view.ModelIdMapperImpl;
import org.waveprotocol.wave.client.wavepanel.view.ViewIdMapper;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProviderImpl;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.impl.BlipViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.BlipViewDomImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipQueueRenderer;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.DomRenderer;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ViewFactories;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ViewFactory;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController;
import org.waveprotocol.wave.client.render.undercurrent.ScreenControllerImpl;
import org.waveprotocol.wave.client.wavepanel.render.RenderCssLoader;
import org.waveprotocol.wave.client.wavepanel.render.DynamicRendererImpl;
import org.waveprotocol.wave.client.wavepanel.render.ObservableDynamicRenderer;
import org.waveprotocol.wave.client.wavepanel.render.FragmentRequester;
import org.waveprotocol.wave.client.wavepanel.render.FragmentRequester.RequestContext;
import org.waveprotocol.wave.client.wavepanel.render.FragmentRequester.Callback;
import org.waveprotocol.wave.client.wavepanel.render.ClientFragmentRequester;
import org.waveprotocol.wave.client.wavepanel.render.ViewportProbe;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationViewAdapter;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexer;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexerImpl;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexerImpl.LoggerContext;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelDebugHook;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelFactory;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelImpl;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListener;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListenerFactory;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.document.indexed.IndexedDocumentImpl;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdGeneratorImpl;
import org.waveprotocol.wave.model.id.IdGeneratorImpl.Seed;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.schema.SchemaProvider;
import org.waveprotocol.wave.model.supplement.LiveSupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.ObservablePrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.supplement.PublicWaveReadStateBootstrap;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl.DefaultFollow;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.util.FuzzingBackOffScheduler;
import org.waveprotocol.wave.model.util.FuzzingBackOffScheduler.CollectiveScheduler;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.Scheduler;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionZeroFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;
import org.waveprotocol.wave.model.wave.opbased.ObservableWaveView;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl.WaveletConfigurator;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl.WaveletFactory;

import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.GwtWaverefEncoder;

import java.util.Collections;
import java.util.Map;

/**
 * The second stage of client code.
 * <p>
 * This stage builds the wave model in memory, and also opens the channel to
 * make it live. Rendering code that operates on the model is also established
 * in this stage.
 *
 */
public interface StageTwo {

    /** @return the (live) conversations in the wave. */
    ObservableConversationView getConversations();

    /** @return the core wave. */
    ObservableWaveView getWave();

    /** @return the signed-in user's (live) supplementary data in the wave. */
    LocalSupplementedWave getSupplement();

    /** @return live blip read/unread information. */
    BlipReadStateMonitor getReadMonitor();

    /** @return the registry of document objects used for conversational blips. */
    WaveDocuments<? extends InteractiveDocument> getDocumentRegistry();

    /** @return the provider of view objects given model objects. */
    ModelAsViewProvider getModelAsViewProvider();

    /** @return the mapper from model identifiers to DOM/view ids. */
    ViewIdMapper getViewIdMapper();

    /** @return the profile manager. */
    ProfileManager getProfileManager();

    /** @return the id generator. */
    IdGenerator getIdGenerator();

    /** @return the communication channel connector. */
    MuxConnector getConnector();

    /**
     * @return the blip content renderer, which needs to be flushed by anything
     *         requiring synchronous rendering.
     */
    BlipQueueRenderer getBlipQueue();

    /** @return controller of diff state. */
    DiffController getDiffController();

    /** @return the signed in user. */
    ParticipantId getSignedInUser();

    /** @return a unique string identifying this session. */
    String getSessionId();

    /** @return stage one. */
    StageOne getStageOne();

    /** @return Reader. */
    Reader getReader();

    /**
     * Default implementation of the stage two configuration. Each component is
     * defined by a factory method, any of which may be overridden in order to
     * stub out some dependencies. Circular dependencies are not detected.
     *
     */
    public static abstract class DefaultProvider extends AsyncHolder.Impl<StageTwo>
            implements StageTwo {
        // Asynchronously constructed and external dependencies
        protected final StageOne stageOne;
        private WaveViewData waveData;

        //
        // Synchronously constructed dependencies.
        //

        // Client stuff.

        private String sessionId;
        private ParticipantId signedInuser;
        private CollectiveScheduler rpcScheduler;

        // Wave stack.

        private IdGenerator idGenerator;
        private WaveDocuments<LazyContentDocument> documentRegistry;
        private WaveletOperationalizer wavelets;
        private WaveViewImpl<OpBasedWavelet> wave;
        private MuxConnector connector;

        // Model objects

        private ProfileManager profileManager;
        private ObservableConversationView conversations;
        private LocalSupplementedWave supplement;
        private BlipReadStateMonitor readMonitor;

        // State Monitors
        private ThreadReadStateMonitor threadReadStateMonitor;

        // Rendering objects.

        private ViewIdMapper viewIdMapper;
        private ShallowBlipRenderer blipDetailer;
        private DomRenderer renderer;
        private BlipQueueRenderer queueRenderer;
        private ModelAsViewProvider modelAsView;
        private DiffController diffController;
        private Reader reader;

        private NewBlipIndicatorPresenter newBlipPresenter;
        private final UnsavedDataListener unsavedDataListener;

        public DefaultProvider(StageOne stageOne, UnsavedDataListener unsavedDataListener) {
            this.stageOne = stageOne;
            this.unsavedDataListener = unsavedDataListener;
        }

        /**
         * Creates the second stage.
         */
        @Override
        protected void create(final Accessor<StageTwo> whenReady) {
            onStageInit();

            final CountdownLatch synchronizer = CountdownLatch.create(2, new Command() {
                @Override
                public void execute() {
                    install();
                    onStageLoaded();
                    whenReady.use(DefaultProvider.this);
                }
            });

            fetchWave(new Accessor<WaveViewData>() {
                @Override
                public void use(WaveViewData x) {
                    waveData = x;
                    synchronizer.tick();
                }
            });

            // Defer everything else, to let the RPC go out.
            SchedulerInstance.getMediumPriorityTimer().scheduleDelayed(new Task() {
                @Override
                public void execute() {
                    installStatics();
                    synchronizer.tick();
                }
            }, 20);
        }

        /** Notifies this provider that the stage is about to be loaded. */
        protected void onStageInit() {
        }

        /** Notifies this provider that the stage has been loaded. */
        protected void onStageLoaded() {
        }

        @Override
        public final StageOne getStageOne() {
            return stageOne;
        }

        @Override
        public final String getSessionId() {
            return sessionId == null ? sessionId = createSessionId() : sessionId;
        }

        @Override
        public final ViewIdMapper getViewIdMapper() {
            return viewIdMapper == null ? viewIdMapper = createViewIdMapper() : viewIdMapper;
        }

        protected final ShallowBlipRenderer getBlipDetailer() {
            return blipDetailer == null ? blipDetailer = createBlipDetailer() : blipDetailer;
        }

        protected final DomRenderer getRenderer() {
            return renderer == null ? renderer = createRenderer() : renderer;
        }

        protected final ThreadReadStateMonitor getThreadReadStateMonitor() {
            return threadReadStateMonitor == null ? threadReadStateMonitor =
                    createThreadReadStateMonitor() : threadReadStateMonitor;
        }

        @Override
        public final BlipQueueRenderer getBlipQueue() {
            return queueRenderer == null ? queueRenderer = createBlipQueueRenderer() : queueRenderer;
        }

        @Override
        public final ModelAsViewProvider getModelAsViewProvider() {
            return modelAsView == null ? modelAsView = createModelAsViewProvider() : modelAsView;
        }

        @Override
        public final ParticipantId getSignedInUser() {
            return signedInuser == null ? signedInuser = createSignedInUser() : signedInuser;
        }

        @Override
        public final IdGenerator getIdGenerator() {
            return idGenerator == null ? idGenerator = createIdGenerator() : idGenerator;
        }

        /** @return the scheduler to use for RPCs. */
        protected final CollectiveScheduler getRpcScheduler() {
            return rpcScheduler == null ? rpcScheduler = createRpcScheduler() : rpcScheduler;
        }

        @Override
        public final ProfileManager getProfileManager() {
            return profileManager == null ? profileManager = createProfileManager() : profileManager;
        }

        @Override
        public final MuxConnector getConnector() {
            return connector == null ? connector = createConnector() : connector;
        }

        @Override
        public final WaveViewImpl<OpBasedWavelet> getWave() {
            return wave == null ? wave = createWave() : wave;
        }

        protected final WaveletOperationalizer getWavelets() {
            return wavelets == null ? wavelets = createWavelets() : wavelets;
        }

        @Override
        public final ObservableConversationView getConversations() {
            return conversations == null ? conversations = createConversations() : conversations;
        }

        @Override
        public final LocalSupplementedWave getSupplement() {
            return supplement == null ? supplement = createSupplement() : supplement;
        }

        @Override
        public final BlipReadStateMonitor getReadMonitor() {
            return readMonitor == null ? readMonitor = createReadMonitor() : readMonitor;
        }

        @Override
        public final WaveDocuments<LazyContentDocument> getDocumentRegistry() {
            return documentRegistry == null
                    ? documentRegistry = createDocumentRegistry() : documentRegistry;
        }

        protected final WaveViewData getWaveData() {
            Preconditions.checkState(waveData != null, "wave not ready");
            return waveData;
        }

        @Override
        public final DiffController getDiffController() {
            return diffController == null ? diffController = createDiffController() : diffController;
        }

        @Override
        public final Reader getReader() {
            return reader;
        }

        /** @return the id mangler for model objects. Subclasses may override. */
        protected ModelIdMapper createModelIdMapper() {
            return ModelIdMapperImpl.create(getConversations(), "UC");
        }

        /** @return the id mangler for view objects. Subclasses may override. */
        protected ViewIdMapper createViewIdMapper() {
            return new ViewIdMapper(createModelIdMapper());
        }

        /** @return the id of the signed-in user. Subclassses may override. */
        protected abstract ParticipantId createSignedInUser();

        /** @return the unique id for this client session. */
        protected abstract String createSessionId();

        /** @return the id generator for model object. Subclasses may override. */
        protected IdGenerator createIdGenerator() {
            final String seed = getSessionId();
            // Replace with session.
            return new IdGeneratorImpl(getSignedInUser().getDomain(), new Seed() {
                @Override
                public String get() {
                    return seed;
                }
            });
        }

        /** @return the scheduler to use for RPCs. Subclasses may override. */
        protected CollectiveScheduler createRpcScheduler() {
            // Use a scheduler that runs closely-timed tasks at the same time.
            return new OptimalGroupingScheduler(SchedulerInstance.getLowPriorityTimer());
        }

        protected WaveletOperationalizer createWavelets() {
            return WaveletOperationalizer.create(getWaveData().getWaveId(), getSignedInUser());
        }

        protected WaveViewImpl<OpBasedWavelet> createWave() {
            WaveViewData snapshot = getWaveData();
            // The operationalizer makes the wavelets function via operation control.
            // The hookup with concurrency-control and remote operation streams occurs
            // later in createUpgrader().
            final WaveletOperationalizer operationalizer = getWavelets();
            WaveletFactory<OpBasedWavelet> waveletFactory = new WaveletFactory<OpBasedWavelet>() {
                @Override
                public OpBasedWavelet create(WaveId waveId, WaveletId id, ParticipantId creator) {
                    long now = System.currentTimeMillis();
                    ObservableWaveletData data = new WaveletDataImpl(id,
                            creator,
                            now,
                            0L,
                            HashedVersion.unsigned(0),
                            now,
                            waveId,
                            getDocumentRegistry());
                    return operationalizer.operationalize(data);
                }
            };
            WaveViewImpl<OpBasedWavelet> wave =
                    WaveViewImpl.create(waveletFactory, snapshot.getWaveId(), getIdGenerator(),
                            getSignedInUser(), WaveletConfigurator.ADD_CREATOR);

            // Populate the initial state.
            for (ObservableWaveletData waveletData : snapshot.getWavelets()) {
                wave.addWavelet(operationalizer.operationalize(waveletData));
            }
            return wave;
        }

        /** @return the conversations in the wave. Subclasses may override. */
        protected ObservableConversationView createConversations() {
            return WaveBasedConversationView.create(getWave(), getIdGenerator());
        }

        /** @return the user supplement of the wave. Subclasses may override. */
        protected LocalSupplementedWave createSupplement() {
            Wavelet udw = getWave().getUserData();
            boolean createdUserData = false;
            if (udw == null) {
                udw = getWave().createUserData();
                createdUserData = true;
            }
            ObservablePrimitiveSupplement state = WaveletBasedSupplement.create(udw);
            if (createdUserData) {
                PublicWaveReadStateBootstrap.seedIfPublicWave(
                        state, getWave(), getSignedInUser());
            }
            ObservableSupplementedWave live = new LiveSupplementedWaveImpl(
                    state, getWave(), getSignedInUser(), DefaultFollow.ALWAYS, getConversations());
            return LocalSupplementedWaveImpl.create(getWave(), live);
        }

        /** @return a supplement to the supplement, to get exact read/unread counts. */
        protected BlipReadStateMonitor createReadMonitor() {
            return BlipReadStateMonitorImpl.create(
                    getWave().getWaveId(), getSupplement(), getConversations());
        }

        /** @return the registry of documents in the wave. Subclasses may override. */
        protected WaveDocuments<LazyContentDocument> createDocumentRegistry() {
            IndexedDocumentImpl.performValidation = false;

            DocumentFactory<?> dataDocFactory =
                    ObservablePluggableMutableDocument.createFactory(createSchemas());
            DocumentFactory<LazyContentDocument> blipDocFactory =
                    new DocumentFactory<LazyContentDocument>() {
                        private final Registries registries = RegistriesHolder.get();

                        @Override
                        public LazyContentDocument create(
                                WaveletId waveletId, String docId, DocInitialization content) {
                            // TODO(piotrkaleta,hearnden): hook up real diff state.
                            SimpleDiffDoc noDiff = SimpleDiffDoc.create(content, null);
                            return LazyContentDocument.create(registries, noDiff);
                        }
                    };

            return WaveDocuments.create(blipDocFactory, dataDocFactory);
        }

        protected abstract SchemaProvider createSchemas();

        /** @return the RPC interface for wave communication. */
        protected abstract WaveViewService createWaveViewService();

        /** @return upgrader for activating stacklets. Subclasses may override. */
        protected MuxConnector createConnector() {
            LoggerBundle logger = LoggerBundle.NOP_IMPL;
            LoggerContext loggers = new LoggerContext(logger, logger, logger, logger);

            IdURIEncoderDecoder uriCodec = new IdURIEncoderDecoder(new ClientPercentEncoderDecoder());
            HashedVersionFactory hashFactory = new HashedVersionZeroFactoryImpl(uriCodec);

            Scheduler scheduler = new FuzzingBackOffScheduler.Builder(getRpcScheduler())
                    .setInitialBackOffMs(ClientFlags.get().initialRpcBackoffMs())
                    .setMaxBackOffMs(ClientFlags.get().maxRpcBackoffMs())
                    .setRandomisationFactor(0.5)
                    .build();

            ViewChannelFactory viewFactory = ViewChannelImpl.factory(createWaveViewService(), logger);
            UnsavedDataListenerFactory unsyncedListeners =
                unsavedDataListener instanceof UnsavedDataListenerFactory
                    ? (UnsavedDataListenerFactory) unsavedDataListener
                    : new UnsavedDataListenerFactory() {

                        private final UnsavedDataListener listener = unsavedDataListener;

                        @Override
                        public UnsavedDataListener create(WaveletId waveletId) {
                            return listener;
                        }

                        @Override
                        public void destroy(WaveletId waveletId) {
                        }
                    };

            WaveletId udwId = getIdGenerator().newUserDataWaveletId(getSignedInUser().getAddress());
            final IdFilter filter = IdFilter.of(Collections.singleton(udwId),
                    Collections.singleton(IdConstants.CONVERSATION_WAVELET_PREFIX));

            WaveletDataImpl.Factory snapshotFactory =
                    WaveletDataImpl.Factory.create(getDocumentRegistry());
            final OperationChannelMultiplexer mux =
                    new OperationChannelMultiplexerImpl(getWave().getWaveId(),
                            viewFactory,
                            snapshotFactory,
                            loggers,
                            unsyncedListeners,
                            scheduler,
                            hashFactory);
            operationChannelMultiplexer = mux;

            final WaveViewImpl<OpBasedWavelet> wave = getWave();

            return new MuxConnector() {
                @Override
                public void connect(Command onOpened) {
                    LiveChannelBinder.openAndBind(getWavelets(),
                            wave,
                            getDocumentRegistry(),
                            mux,
                            filter,
                            onOpened);
                }

                @Override
                public void close() {
                    mux.close();
                    if (operationChannelMultiplexer == mux) {
                        operationChannelMultiplexer = null;
                    }
                }
            };
        }

        /** @return the manager of user identities. Subclasses may override. */
        protected ProfileManager createProfileManager() {
            return new ProfileManagerImpl();
        }

        /** @return the renderer of intrinsic blip state. Subclasses may override. */
        protected ShallowBlipRenderer createBlipDetailer() {
            return new UndercurrentShallowBlipRenderer(getProfileManager(), getSupplement(),
                    DateUtils.getInstance());
        }

        /** @return the thread state monitor. Subclasses may override. */
        protected ThreadReadStateMonitor createThreadReadStateMonitor() {
            return ThreadReadStateMonitorImpl.create(getSupplement(), getConversations());
        }

        /** @return the renderer of intrinsic blip state. Subclasses may override. */
        protected BlipQueueRenderer createBlipQueueRenderer() {
            DomAsViewProvider domAsView = stageOne.getDomAsViewProvider();
            ReplyManager replyManager = new ReplyManager(getModelAsViewProvider());

            // Add all doodads here.
            DocumentRegistries doodads = installDoodads(DocumentRegistries.builder()) // \u2620
                    .use(InlineAnchorLiveRenderer.installer(getViewIdMapper(), replyManager,
                            domAsView))
                    .build();

            LiveConversationViewRenderer live =
                    LiveConversationViewRenderer.create(SchedulerInstance.getLowPriorityTimer(),
                            getConversations(), getModelAsViewProvider(), getBlipDetailer(),
                            replyManager,
                            getThreadReadStateMonitor(), getProfileManager(), getSupplement());
            live.init();

            if (Session.get().hasFeature("compact-inline-blips")) {
                Document.get().getBody().addClassName("compact-inline-blips");
            }

            // Install new blip indicator pill (feature-flagged via server-side flags).
            if (Session.get().hasFeature("new-blip-indicator")) {
                newBlipPresenter = new NewBlipIndicatorPresenter(getSignedInUser());
                live.setNewBlipIndicatorPresenter(newBlipPresenter);
            }

            // Hook quasi-deletion UI: mark blip DOM prior to removal.
            if (Boolean.TRUE.equals(ClientFlags.get().enableQuasiDeletionUi())) {
                final QuasiConversationViewAdapter qa = getQuasiAdapter();
                if (qa != null) {
                    qa.addListener(new QuasiConversationViewAdapter.Listener() {
                        @Override
                        public void onBeforeBlipQuasiRemoved(ObservableConversationBlip blip,
                                                             WaveletOperationContext ctx) {
                            BlipView v = getModelAsViewProvider().getBlipView(blip);
                            if (v instanceof BlipViewImpl) {
                                Object intrinsic = ((BlipViewImpl<?>) v).getIntrinsic();
                                if (intrinsic instanceof BlipViewDomImpl) {
                                    BlipViewDomImpl dom = (BlipViewDomImpl) intrinsic;
                                    dom.setQuasiDeleted(true);
                                    // Build tooltip from ctx if available, else fallback to blip meta
                                    String author = "unknown";
                                    long timestamp = blip.getLastModifiedTime();
                                    if (ctx != null) {
                                        if (ctx.getCreator() != null && ctx.getCreator().getAddress() != null) {
                                            author = ctx.getCreator().getAddress();
                                        }
                                        if (ctx.getTimestamp() > 0) {
                                            timestamp = ctx.getTimestamp();
                                        }
                                    }
                                    else {
                                        ParticipantId pid = blip.getAuthorId();
                                        if (pid != null) {
                                            String addr = pid.getAddress();
                                            if (addr != null && !addr.isEmpty()) {
                                                author = addr;
                                            }
                                            try {
                                                Profile p = getProfileManager().getProfile(pid);
                                                if (p != null) {
                                                    String name = p.getFullName();
                                                    if (name != null && !name.trim().isEmpty()) {
                                                        author = name;
                                                    }
                                                }
                                            }
                                            catch (Exception ex) {
                                                GWT.log("Quasi tooltip: profile lookup failed for " + pid, ex);
                                            }
                                        }
                                    }
                                    String time =
                                            DateUtils.getInstance().formatDateTime(new Date(timestamp));
                                    String tooltip = "Deleted by " + author + " at " + time;
                                    dom.getElement().setTitle(tooltip);
                                }
                            }
                        }

                        @Override
                        public void onBlipQuasiRemoved(ObservableConversationBlip blip,
                                                       WaveletOperationContext ctx) { /* no-op */ }

                        @Override
                        public void onBeforeThreadQuasiRemoved(ObservableConversationThread thread,
                                                               WaveletOperationContext ctx) { /*
                                                               no-op */ }

                        @Override
                        public void onThreadQuasiRemoved(ObservableConversationThread thread,
                                                         WaveletOperationContext ctx) { /* no-op */ }
                    });
                }
            }

            BlipPager pager = BlipPager.create(
                    getDocumentRegistry(), doodads, domAsView, getModelAsViewProvider(),
                    getBlipDetailer(),
                    stageOne.getWavePanel().getGwtPanel());

            // Collect various components required for paging blips in/out.
            pagingHandlerProxy = PagingHandlerProxy.create( // \u2620
                    // Enables and disables the document rendering, as well blip metadata.
                    pager,
                    // Registers and deregisters profile listeners for name changes.
                    live);

            // When dynamic rendering is enabled, attach a resource cleaner to page-out
            if (Boolean.TRUE.equals(ClientFlags.get().enableDynamicRendering())) {
                pager.setResourceCleaner(new BlipResourceCleaner(stageOne.getWavePanel().getGwtPanel()));
            }

            return BlipQueueRenderer.create(pagingHandlerProxy);
        }

        protected ViewFactory createViewFactories() {
            return ViewFactories.FIXED;
        }

        protected DomRenderer createRenderer() {
            final BlipQueueRenderer pager = getBlipQueue();
            final boolean dynamic = Boolean.TRUE.equals(ClientFlags.get().enableDynamicRendering());
            DocRefRenderer docRenderer = new DocRefRenderer() {
                @Override
                public UiBuilder render(
                        ConversationBlip blip, IdentityMap<ConversationThread, UiBuilder> replies) {
                    // Ensure initial content is visible regardless of dynamic mode by enqueueing
                    // for paging during static render. Dynamic renderer will page-out as needed.
                    pager.add(blip);
                    // Documents are rendered blank, and filled in later when they get paged in.
                    return DocRefRenderer.EMPTY.render(blip, replies);
                }
            };

            RenderingRules<UiBuilder> rules = new FullDomRenderer(
                    getBlipDetailer(), docRenderer, getProfileManager(),
                    getViewIdMapper(), createViewFactories(), getThreadReadStateMonitor(),
                    getSupplement(), getSignedInUser());
            return new HtmlDomRenderer(ReductionBasedRenderer.of(rules, getConversations()));
        }

        protected DiffController createDiffController() {
            return DiffController.create(
                    getConversations(), getSupplement(), getDocumentRegistry(),
                    getModelAsViewProvider());
        }

        /**
         * Fetches and builds the core wave state.
         *
         * @param whenReady command to execute when the wave is built
         */
        protected abstract void fetchWave(final Accessor<WaveViewData> whenReady);

        /**
         * Installs parts of stage two that have no dependencies.
         * <p>
         * Subclasses may override this to change the set of installed features.
         */
        protected void installStatics() {
            WavePanelResourceLoader.loadCss();
        }

        protected DocumentRegistries.Builder installDoodads(DocumentRegistries.Builder doodads) {
            return doodads.use(new DoodadInstallers.GlobalInstaller() {
                @Override
                public void install(Registries r) {
                    DiffAnnotationHandler.register(r.getAnnotationHandlerRegistry(),
                            r.getPaintRegistry());
                    DiffDeleteRenderer.register(r.getElementHandlerRegistry());
                    StyleAnnotationHandler.register(r);
                    TitleAnnotationHandler.register(r);
                    LinkAnnotationHandler.register(r, createLinkAttributeAugmenter());
                    MentionAnnotationHandler.register(r, getProfileManager());
                    TaskAnnotationHandler.register(r);
                    SelectionAnnotationHandler.register(r, getSessionId(), getProfileManager());
                    ImageThumbnail.register(r.getElementHandlerRegistry(),
                            AttachmentManagerProvider.get(),
                            new ImageThumbnail.ThumbnailActionHandler() {

                                @Override
                                public boolean onClick(ImageThumbnailWrapper thumbnail) {
                                    return handleThumbnailClick(thumbnail);
                                }
                            });
                }
            });
        }

        /**
         * Handles a click on an image thumbnail by opening it in the lightbox overlay
         * (if the attachment is an image) or returning false to allow the default
         * window-open behaviour for non-image attachments.
         *
         * @return true if the click was handled (lightbox opened), false otherwise.
         */
        private boolean handleThumbnailClick(ImageThumbnailWrapper thumbnail) {
            if (thumbnail == null || thumbnail.getAttachment() == null) {
                return false;
            }
            String url = thumbnail.getAttachment().getAttachmentUrl();
            String mimeType = thumbnail.getAttachment().getMimeType();
            String caption = thumbnail.getCaptionText();
            if (url == null || url.isEmpty()) {
                return false;
            }
            // Open lightbox for image types. Check MIME type first, then fall
            // back to image metadata presence (covers cases where mime is null
            // but the server already detected the attachment as an image).
            boolean isImage = (mimeType != null && mimeType.startsWith("image/"))
                    || thumbnail.getAttachment().getContentImageMetadata() != null;
            if (isImage) {
                nativeOpenLightbox(url, caption != null ? caption : "");
                return true;
            }
            return false;
        }

        /**
         * Calls into the page-level lightbox JavaScript via JSNI.
         */
        private native void nativeOpenLightbox(String url, String caption) /*-{
            if ($wnd.openWaveLightbox) {
                $wnd.openWaveLightbox(url, caption);
            }
        }-*/;

        /**
         * Key used to register the internal-link click handler in the
         * {@link AnnotationPaint} event-handler registry.
         */
        private static final String INTERNAL_LINK_HANDLER_KEY = "internalLink";

        /**
         * True once the static internal-link event handler has been registered.
         * Guarded by the class-level monitor since {@code installDoodads} can
         * theoretically be called more than once.
         */
        private static boolean internalLinkHandlerRegistered = false;

        /**
         * Ensures the internal-link click handler is registered exactly once
         * with {@link AnnotationPaint}.
         */
        private static void ensureInternalLinkHandlerRegistered() {
            if (!internalLinkHandlerRegistered) {
                AnnotationPaint.registerEventHandler(INTERNAL_LINK_HANDLER_KEY,
                    new AnnotationPaint.EventHandler() {
                        @Override
                        public void onEvent(ContentElement node, Event event) {
                            if (event.getTypeInt() != Event.ONCLICK) {
                                return;
                            }
                            Element target = Element.as(event.getEventTarget());
                            // Walk up to find the anchor element (the click may
                            // be on an inner text node).
                            while (target != null
                                    && !"a".equalsIgnoreCase(target.getTagName())) {
                                target = target.getParentElement();
                            }
                            if (target == null) {
                                return;
                            }
                            String href = target.getAttribute("href");
                            if (href == null || !href.startsWith("#")) {
                                return;
                            }
                            // Prevent the browser from performing default anchor navigation.
                            event.preventDefault();

                            String encodedRef = href.substring(1);
                            try {
                                WaveRef ref = GwtWaverefEncoder.decodeWaveRefFromPath(encodedRef);
                                ClientEvents.get().fireEvent(new WaveSelectionEvent(ref));
                            } catch (Exception e) {
                                // Malformed ref -- nothing we can do, just ignore.
                            }
                        }
                    });
                internalLinkHandlerRegistered = true;
            }
        }

        protected LinkAttributeAugmenter createLinkAttributeAugmenter() {
            // Make sure the static click handler is available.
            ensureInternalLinkHandlerRegistered();

            return new LinkAttributeAugmenter() {
                @Override
                public Map<String, String> augment(Map<String, Object> annotations, boolean isEditing,
                                                   Map<String, String> current) {
                    String link = current.get(AnnotationPaint.LINK_ATTR);
                    if (link != null && link.startsWith("#")) {
                        // Internal link -- attach the click handler so that
                        // navigation works even when the editor is in edit mode.
                        java.util.HashMap<String, String> augmented =
                            new java.util.HashMap<String, String>(current);
                        augmented.put(AnnotationPaint.MOUSE_LISTENER_ATTR,
                            INTERNAL_LINK_HANDLER_KEY);
                        return augmented;
                    }
                    return current;
                }
            };
        }

        protected ModelAsViewProvider createModelAsViewProvider() {
            return new ModelAsViewProviderImpl(getViewIdMapper(), stageOne.getDomAsViewProvider());
        }

        /**
         * Installs parts of stage two that have dependencies.
         * <p>
         * This method is only called once all asynchronously loaded components of
         * stage two are ready.
         * <p>
         * Subclasses may override this to change the set of installed features.
         */
        protected void install() {
            // Install diff control before rendering, because logical diff state may
            // need to be adjusted due to arbitrary UI policies.
            getDiffController().install();

            // Install rendering capabilities, then render if necessary.
            stageOne.getDomAsViewProvider().setRenderer(getRenderer());
            ensureRendered();

            configureFragmentsAndDynamicRendering();

            // Install eager UI features
            installFeatures();

            // Activate liveness.
            getConnector().connect(null);
        }

        protected void configureFragmentsAndDynamicRendering() {
            boolean dynamicEnabled = Boolean.TRUE.equals(ClientFlags.get().enableDynamicRendering());
            boolean viewFetchEnabled = Boolean.TRUE.equals(ClientFlags.get().enableFragmentFetchViewChannel());
            boolean forceLayerEnabled = Boolean.TRUE.equals(ClientFlags.get().enableFragmentFetchForceLayer());
            String fragmentMode = null;
            boolean httpModeRequested = false;
            try {
                fragmentMode = ClientFlags.get().fragmentFetchMode();
                httpModeRequested = "http".equals(fragmentMode);
            } catch (Throwable t) {
                GWT.log("Failed to get fragment fetch mode", t);
            }
            boolean fragmentFetchEnabled = viewFetchEnabled || forceLayerEnabled || httpModeRequested;
            try {
                FragmentsDebugIndicator.setClientFlags(fragmentMode, fragmentFetchEnabled, dynamicEnabled);
            } catch (Throwable t) {
                GWT.log("Failed to set fragment fetch mode", t);
            }
            try {
                GWT.log("StageTwo: dynamic=" + dynamicEnabled + ", fragmentMode=" + fragmentMode + ", fetchFlag=" + fragmentFetchEnabled);
            } catch (Throwable t) {
                GWT.log("Failed to set debug mode", t);
            }
            try {
                ViewChannelImpl.setFragmentsDebugHook(new ViewChannelDebugHook() {
                    @Override
                    public void onRangesReceived(int rangeCount) {
                        FragmentsDebugIndicator.onRanges(rangeCount);
                    }
                });
                ViewChannelImpl.setFragmentsApplier(new ClientStatsRawFragmentsApplier());
                Boolean ena = ClientFlags.get().enableFragmentsApplier();
                Boolean force = ClientFlags.get().forceClientFragments();
                Integer clamp = ClientFlags.get().fragmentsApplierMaxRanges();
                boolean enableApplier = true;
                if (ena != null) {
                    enableApplier = ena.booleanValue();
                }
                if (force != null && force.booleanValue()) {
                    enableApplier = true;
                }
                if (clamp != null) {
                    int capped = clamp.intValue();
                    if (capped <= 0) {
                        capped = -1;
                    }
                    ViewChannelImpl.setApplierMaxRangesPerApply(capped);
                } else {
                    ViewChannelImpl.setApplierMaxRangesPerApply(-1);
                }
                ViewChannelImpl.setFragmentsApplierEnabled(enableApplier);
                try {
                    GWT.log("enableFragmentsApplier=" + enableApplier + ", fragmentFetchMode=" + ClientFlags.get().fragmentFetchMode());
                } catch (Throwable ignore) {
                }
                if (enableApplier) {
                    try {
                        ClientStatsRawFragmentsApplier.ping();
                    } catch (Throwable ignore) {
                    }
                    try {
                        GWT.log("Client fragments applier wired (dev)");
                    } catch (Throwable ignore) {
                    }
                }
                try {
                    FragmentsDebugIndicator.setApplierEnabled(enableApplier);
                } catch (Throwable ignore) {
                }
            } catch (Throwable ignore) {
            }
            if (Boolean.TRUE.equals(ClientFlags.get().enableQuasiDeletionUi())) {
                initQuasiAdapter();
            }
            if (dynamicEnabled) {
                RenderCssLoader.ensureInjected();
            }
            if (dynamicEnabled) {
                try {
                    ScreenController screen = ScreenControllerImpl.createDefault();
                    if (screen != null && getConversations() != null && getModelAsViewProvider() != null
                            && getBlipQueue() != null && getPagingHandler() != null) {
                        FragmentRequester requester;
                        boolean fetchEnabled = fragmentFetchEnabled;
                        // Stream mode is canonical: if fragmentFetchMode=stream, allow it
                        // regardless of the legacy enableFragmentFetchViewChannel flag.
                        boolean allowStream = viewFetchEnabled || forceLayerEnabled
                                || "stream".equals(fragmentMode);
                        if (!fetchEnabled) {
                            try { GWT.log("Dynamic fragments: client fetch disabled; using NO_OP requester"); }
                            catch (Throwable ignore) {}
                        }
                        if (fetchEnabled && fragmentMode != null) {
                            switch (fragmentMode) {
                                case "stream":
                                    if (!allowStream) {
                                        requester = FragmentRequester.NO_OP;
                                    } else {
                                        final ClientFragmentRequester httpRequester = new ClientFragmentRequester();
                                        final ViewChannelFragmentRequester streamRequester = new ViewChannelFragmentRequester(new ViewChannelFragmentRequester.ChannelSupplier() {
                                            @Override public ViewChannel get() {
                                                return operationChannelMultiplexer != null
                                                        ? operationChannelMultiplexer.getViewChannel()
                                                        : null;
                                            }
                                        });
                                        final boolean force = forceLayerEnabled;
                                        requester = new FragmentRequester() {
                                            private boolean loggedHttpFallback = false;
                                            private boolean loggedStreamUpgrade = false;
                                            private boolean loggedForcedLayer = false;

                                            @Override public void fetch(RequestContext request, Callback cb) {
                                                ViewChannel channel = operationChannelMultiplexer != null
                                                        ? operationChannelMultiplexer.getViewChannel()
                                                        : null;
                                                if (channel != null || force) {
                                                    if (force && channel == null && !loggedForcedLayer) {
                                                        try { GWT.log("Dynamic fragments: force-layer override active; attempting ViewChannel fetch without cache warmup"); }
                                                        catch (Throwable ignore) {}
                                                        loggedForcedLayer = true;
                                                    }
                                                    if (!loggedStreamUpgrade) {
                                                        try { GWT.log("Dynamic fragments: switching to ViewChannel fetch"); }
                                                        catch (Throwable ignore) {}
                                                        loggedStreamUpgrade = true;
                                                    }
                                                    streamRequester.fetch(request, cb);
                                                } else {
                                                    if (!loggedHttpFallback) {
                                                        try { GWT.log("Dynamic fragments: ViewChannel not ready, using HTTP fallback"); }
                                                        catch (Throwable ignore) {}
                                                        loggedHttpFallback = true;
                                                    }
                                                    httpRequester.fetch(request, cb);
                                                }
                                            }
                                        };
                                    }
                                    break;
                                case "http":
                                    requester = new ClientFragmentRequester();
                                    try { GWT.log("Dynamic fragments: using HTTP fragment requester (metrics-only; payload not applied)"); }
                                    catch (Throwable ignore) {}
                                    break;
                                case "off":
                                    requester = FragmentRequester.NO_OP;
                                    break;
                                default:
                                    requester = FragmentRequester.NO_OP;
                                    try { GWT.log("Dynamic fragments: unknown fragmentFetchMode '" + fragmentMode + "'; using NO_OP requester"); }
                                    catch (Throwable ignore) {}
                            }
                        } else {
                            requester = FragmentRequester.NO_OP;
                        }
                        ObservableDynamicRenderer<Element> dyn = DynamicRendererImpl.create(
                                getConversations(), getModelAsViewProvider(), getBlipQueue(),
                                getPagingHandler(),
                                requester,
                                screen);
                        dyn.init();
                        if (Boolean.TRUE.equals(ClientFlags.get().enableViewportStats())) {
                            ViewportProbe.runOnce("init");
                        }
                    }
                } catch (Exception ex) {
                    GWT.log("StageTwo: dynamic rendering init failed", ex);
                }
            }
        }

        // Quasi-deletion adapter for renderers interested in pre-delete callbacks.
        private QuasiConversationViewAdapter quasiAdapter;
        private PagingHandlerProxy pagingHandlerProxy;
        private OperationChannelMultiplexer operationChannelMultiplexer;

        protected QuasiConversationViewAdapter getQuasiAdapter() {
            if (quasiAdapter == null && Boolean.TRUE.equals(ClientFlags.get().enableQuasiDeletionUi())) {
                try {
                    quasiAdapter = new QuasiConversationViewAdapter(getConversations());
                }
                catch (Throwable ignore) {
                }
            }
            return quasiAdapter;
        }

        private void initQuasiAdapter() {
            if (quasiAdapter == null) {
                quasiAdapter = new QuasiConversationViewAdapter(getConversations());
            }
        }

        protected PagingHandlerProxy getPagingHandler() {
            return pagingHandlerProxy;
        }

        /**
         * Ensures that the wave is rendered.
         * <p>
         * Subclasses may override (e.g., to use server-side rendering).
         */
        protected void ensureRendered() {
            // Default behaviour is to render the whole wave.
            Element e = getRenderer().render(getConversations());
            stageOne.getWavePanel().init(e);
        }

        /**
         * Installs the eager features of this stage.
         */
        protected void installFeatures() {
            // Eagerly install some features.
            reader = Reader.install(getSupplement(), stageOne.getFocusFrame(),
                    getModelAsViewProvider(),
                    getDocumentRegistry());

            // Wire collapse/expand persistence into the user-data wavelet.
            stageOne.getCollapser().init(getSupplement(), getModelAsViewProvider());

            // Attach new-blip indicator pill to the conversation scroll container.
            // DOM is guaranteed to exist after ensureRendered().
            if (newBlipPresenter != null) {
                ObservableConversation root = getConversations().getRoot();
                if (root == null) {
                    return;
                }
                String convDomId = getViewIdMapper().conversationOf(root);
                Element threadContainer = Document.get().getElementById(
                    org.waveprotocol.wave.client.wavepanel.view.dom.full
                        .TopConversationViewBuilder.Components.THREAD_CONTAINER
                        .getDomId(convDomId));
                if (threadContainer != null) {
                    newBlipPresenter.attach(threadContainer);
                }
            }
        }
    }
}
