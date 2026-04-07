/** Jakarta variant of DataApiOperationServiceRegistry. */
package org.waveprotocol.box.server.robots.dataapi;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.wave.api.OperationType;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.authentication.email.PublicBaseUrlResolver;
import org.waveprotocol.box.server.robots.AbstractOperationServiceRegistry;
import org.waveprotocol.box.server.robots.operations.*;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;

public final class DataApiOperationServiceRegistry extends AbstractOperationServiceRegistry {
  @SuppressWarnings("deprecation")
  @Inject
  public DataApiOperationServiceRegistry(Injector injector) {
    Config config = injector.getInstance(Config.class);
    String rpcServerUrl = PublicBaseUrlResolver.resolve(config) + RobotsGateway.DATA_API_RPC_PATH;
    register(OperationType.ROBOT_NOTIFY, DoNothingService.create());
    register(OperationType.ROBOT_NOTIFY_CAPABILITIES_HASH, DoNothingService.create());
    register(OperationType.WAVELET_ADD_PARTICIPANT_NEWSYNTAX, ParticipantServices.create());
    register(OperationType.WAVELET_APPEND_BLIP, BlipOperationServices.create());
    register(OperationType.WAVELET_REMOVE_PARTICIPANT_NEWSYNTAX, ParticipantServices.create());
    register(OperationType.BLIP_CONTINUE_THREAD, BlipOperationServices.create());
    register(OperationType.BLIP_CREATE_CHILD, BlipOperationServices.create());
    register(OperationType.BLIP_DELETE, BlipOperationServices.create());
    register(OperationType.DOCUMENT_APPEND_INLINE_BLIP, BlipOperationServices.create());
    register(OperationType.DOCUMENT_APPEND_MARKUP, BlipOperationServices.create());
    register(OperationType.DOCUMENT_INSERT_INLINE_BLIP, BlipOperationServices.create());
    register(OperationType.DOCUMENT_INSERT_INLINE_BLIP_AFTER_ELEMENT, BlipOperationServices.create());
    register(OperationType.ROBOT_CREATE_WAVELET, CreateWaveletService.create());
    register(OperationType.ROBOT_FETCH_WAVE, FetchWaveService.create(rpcServerUrl));
    register(OperationType.DOCUMENT_MODIFY, DocumentModifyService.create());
    register(OperationType.ROBOT_SEARCH, injector.getInstance(SearchService.class));
    register(OperationType.WAVELET_SET_TITLE, WaveletSetTitleService.create());
    register(OperationType.ROBOT_FOLDER_ACTION, FolderActionService.create());
    register(OperationType.ROBOT_FETCH_PROFILES, injector.getInstance(FetchProfilesService.class));
    register(OperationType.ROBOT_EXPORT_SNAPSHOT, ExportSnapshotService.create());
    register(OperationType.ROBOT_EXPORT_DELTAS, ExportDeltasService.create());
    register(OperationType.ROBOT_EXPORT_ATTACHMENT, injector.getInstance(ExportAttachmentService.class));
    register(OperationType.ROBOT_IMPORT_DELTAS, injector.getInstance(ImportDeltasService.class));
    register(OperationType.ROBOT_IMPORT_ATTACHMENT, injector.getInstance(ImportAttachmentService.class));
  }
}
