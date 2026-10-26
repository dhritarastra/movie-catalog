package com.moviecatalog.catalog.movie

import com.moviecatalog.catalog.v1.MovieServiceGrpc
import org.springframework.stereotype.Service

/**
 * gRPC endpoint for movies. Spring gRPC registers every BindableService bean on the server
 * (and only starts the server once at least one exists). Methods not overridden yet return
 * UNIMPLEMENTED; they are filled in slice by slice (docs/PLAN.md §11).
 */
@Service
class MovieGrpcService : MovieServiceGrpc.MovieServiceImplBase()
