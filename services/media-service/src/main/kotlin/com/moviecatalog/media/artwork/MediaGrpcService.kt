package com.moviecatalog.media.artwork

import com.moviecatalog.media.v1.MediaServiceGrpc
import org.springframework.stereotype.Service

/**
 * gRPC endpoint for artwork. Spring gRPC registers every BindableService bean on the server
 * (and only starts the server once at least one exists). Methods not overridden yet return
 * UNIMPLEMENTED; they are filled in slice by slice (docs/PLAN.md §11).
 */
@Service
class MediaGrpcService : MediaServiceGrpc.MediaServiceImplBase()
