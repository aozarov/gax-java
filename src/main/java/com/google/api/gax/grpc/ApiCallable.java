/*
 * Copyright 2015, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.api.gax.grpc;

import com.google.api.gax.core.RetryParams;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.Channel;
import io.grpc.ExperimentalApi;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nullable;

/**
 * A callable is an object which represents one or more rpc calls. Various operators on callables
 * produce new callables, representing common API programming patterns. Callables can be used to
 * directly operate against an api, or to efficiently implement wrappers for apis which add
 * additional functionality and processing.
 */
@ExperimentalApi
public class ApiCallable<RequestT, ResponseT> {
  private final FutureCallable<RequestT, ResponseT> callable;

  private ApiCallable(FutureCallable<RequestT, ResponseT> callable) {
    this.callable = callable;
  }

  /**
   * Perform a call asynchronously. If the {@link io.grpc.Channel} encapsulated in the given
   * {@link com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound,
   * using {@link #bind(Channel)}.
   *
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return {@link com.google.common.util.concurrent.ListenableFuture} for the call result
   */
  public ListenableFuture<ResponseT> futureCall(CallContext<RequestT> context) {
    return callable.futureCall(context);
  }

  /**
   * Same as {@link #futureCall(CallContext)}, with null {@link io.grpc.Channel} and
   * default {@link io.grpc.CallOptions}.
   *
   * @param request request
   * @return {@link com.google.common.util.concurrent.ListenableFuture} for the call result
   */
  public ListenableFuture<ResponseT> futureCall(RequestT request) {
    return futureCall(CallContext.<RequestT>of(request));
  }

  /**
   * Perform a call synchronously. If the {@link io.grpc.Channel} encapsulated in the given
   * {@link com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound,
   * using {@link #bind(Channel)}.
   *
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @return the call result
   */
  public ResponseT call(CallContext<RequestT> context) {
    return Futures.getUnchecked(futureCall(context));
  }

  /**
   * Same as {@link #call(CallContext)}, with null {@link io.grpc.Channel} and
   * default {@link io.grpc.CallOptions}.
   *
   * @param request request
   * @return the call result
   */
  public ResponseT call(RequestT request) {
    return Futures.getUnchecked(futureCall(request));
  }

  /**
   * Perform a call asynchronously with the given {@code observer}.
   * If the {@link io.grpc.Channel} encapsulated in the given
   * {@link com.google.api.gax.grpc.CallContext} is null, a channel must have already been bound,
   * using {@link #bind(Channel)}.
   *
   * @param context {@link com.google.api.gax.grpc.CallContext} to make the call with
   * @param observer Observer to interact with the result
   */
  public void asyncCall(CallContext<RequestT> context, final StreamObserver<ResponseT> observer) {
    Futures.addCallback(
        futureCall(context),
        new FutureCallback<ResponseT>() {
          @Override
          public void onFailure(Throwable t) {
            if (observer != null) {
              observer.onError(t);
            }
          }

          @Override
          public void onSuccess(ResponseT result) {
            if (observer != null) {
              observer.onNext(result);
              observer.onCompleted();
            }
          }
        });
  }

  /**
   * Same as {@link #asyncCall(CallContext, StreamObserver)}, with null {@link io.grpc.Channel} and
   * default {@link io.grpc.CallOptions}.
   *
   * @param request request
   * @param observer Observer to interact with the result
   */
  public void asyncCall(RequestT request, StreamObserver<ResponseT> observer) {
    asyncCall(CallContext.<RequestT>of(request), observer);
  }

  /**
   * Creates a callable which can execute the described gRPC method.
   */
  public static <ReqT, RespT> ApiCallable<ReqT, RespT> create(
      MethodDescriptor<ReqT, RespT> descriptor) {
    return ApiCallable.<ReqT, RespT>create(new DescriptorClientCallFactory<>(descriptor));
  }

  /**
   * Creates a callable which uses the {@link io.grpc.ClientCall}
   * generated by the given {@code factory}.
   */
  public static <ReqT, RespT> ApiCallable<ReqT, RespT> create(
      ClientCallFactory<ReqT, RespT> factory) {
    return ApiCallable.<ReqT, RespT>create(new DirectCallable<>(factory));
  }

  /**
   * Creates a callable which uses the given {@link FutureCallable}.
   */
  public static <ReqT, RespT> ApiCallable<ReqT, RespT> create(
      FutureCallable<ReqT, RespT> callable) {
    return new ApiCallable<ReqT, RespT>(callable);
  }

  /**
   * Create a callable with a bound channel. If a call is made without specifying a channel,
   * the {@code boundChannel} is used instead.
   */
  public ApiCallable<RequestT, ResponseT> bind(Channel boundChannel) {
    return new ApiCallable<RequestT, ResponseT>(
        new ChannelBindingCallable<RequestT, ResponseT>(callable, boundChannel));
  }

  /**
   * Creates a callable whose calls raise {@link ApiException}
   * instead of the usual {@link io.grpc.StatusRuntimeException}.
   * The {@link ApiException} will consider failures with any of the given status codes
   * retryable.
   */
  public ApiCallable<RequestT, ResponseT> retryableOn(ImmutableSet<Status.Code> retryableCodes) {
    return new ApiCallable<RequestT, ResponseT>(
        new ExceptionTransformingCallable<>(callable, retryableCodes));
  }

  /**
   * Creates a callable which retries using exponential back-off. Back-off parameters are defined
   * by the given {@code retryParams}.
   */
  public ApiCallable<RequestT, ResponseT> retrying(
      RetryParams retryParams, ScheduledExecutorService executor) {
    return new ApiCallable<RequestT, ResponseT>(
        new RetryingCallable<RequestT, ResponseT>(callable, retryParams, executor));
  }

  /**
   * Returns a callable which streams the resources obtained from a series of calls to a method
   * implementing the pagination pattern.
   */
  public <ResourceT> ApiCallable<RequestT, Iterable<ResourceT>> pageStreaming(
      PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
    return new ApiCallable<RequestT, Iterable<ResourceT>>(
        new PageStreamingCallable<RequestT, ResponseT, ResourceT>(callable, pageDescriptor));
  }

  /**
   * Returns a callable which bundles the call, meaning that multiple requests are bundled
   * together and sent at the same time.
   */
  public ApiCallable<RequestT, ResponseT> bundling(
      BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor,
      BundlerFactory<RequestT, ResponseT> bundlerFactory) {
    return new ApiCallable<RequestT, ResponseT>(
        new BundlingCallable<RequestT, ResponseT>(callable, bundlingDescriptor, bundlerFactory));
  }

  /**
   * A builder for ApiCallable.
   */
  public static class ApiCallableBuilder<RequestT, ResponseT> extends ApiCallSettings {
    private final ApiCallable<RequestT, ResponseT> baseCallable;

    /**
     * Constructs an instance of ApiCallableBuilder.
     *
     * @param grpcMethodDescriptor A method descriptor obtained from the generated GRPC
     * class.
     */
    public ApiCallableBuilder(MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor) {
      this.baseCallable = ApiCallable.create(grpcMethodDescriptor);
    }

    /**
     * Builds an ApiCallable using the settings provided.
     *
     * @param serviceApiSettings Provides the channel and executor.
     */
    public ApiCallable<RequestT, ResponseT> build(ServiceApiSettings serviceApiSettings)
        throws IOException {
      ApiCallable<RequestT, ResponseT> callable = baseCallable;

      ManagedChannel channel = serviceApiSettings.getChannel();
      ScheduledExecutorService executor = serviceApiSettings.getExecutor();

      if (getRetryableCodes() != null) {
        callable = callable.retryableOn(ImmutableSet.copyOf(getRetryableCodes()));
      }

      if (getRetryParams() != null) {
        callable = callable.retrying(getRetryParams(), executor);
      }

      callable = callable.bind(channel);

      return callable;
    }
  }

  /**
   * A builder for an ApiCallable which presents an Iterable backed by page
   * streaming calls to an API method.
   */
  public static class PageStreamingApiCallableBuilder<RequestT, ResponseT, ResourceT>
      extends ApiCallableBuilder<RequestT, ResponseT> {
    private final PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;

    /**
     * Constructs an instance of ApiCallableBuilder.
     *
     * @param grpcMethodDescriptor A method descriptor obtained from the generated GRPC
     * class.
     * @param pageDescriptor An object which injects and extracts fields related
     * to page streaming for a particular API method.
     */
    public PageStreamingApiCallableBuilder(
        MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
        PageStreamingDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor) {
      super(grpcMethodDescriptor);
      this.pageDescriptor = pageDescriptor;
    }

    /**
     * Builds an ApiCallable with an Iterable response using the settings provided.
     *
     * @param serviceApiSettings Provides the channel and executor.
     */
    public ApiCallable<RequestT, Iterable<ResourceT>> buildPageStreaming(
        ServiceApiSettings serviceApiSettings) throws IOException {
      return build(serviceApiSettings).pageStreaming(pageDescriptor);
    }
  }

  /**
   * A pair of an ApiCallable and its associated BundlerFactory.
   */
  @AutoValue
  public static abstract class BundlableApiCallableInfo<RequestT, ResponseT> {
    public static <RequestT, ResponseT> BundlableApiCallableInfo<RequestT, ResponseT> create(
        ApiCallable<RequestT, ResponseT> apiCallable,
        @Nullable BundlerFactory<RequestT, ResponseT> bundlerFactory) {
      return new AutoValue_ApiCallable_BundlableApiCallableInfo<>(apiCallable, bundlerFactory);
    }

    /**
     * Returns the ApiCallable.
     */
    public abstract ApiCallable<RequestT, ResponseT> getApiCallable();

    /**
     * Returns the BundlerFactory.
     */
    @Nullable
    public abstract BundlerFactory<RequestT, ResponseT> getBundlerFactory();
  }

  /**
   * A builder for an ApiCallable with bundling support.
   */
  public static class BundlableApiCallableBuilder<RequestT, ResponseT>
      extends ApiCallableBuilder<RequestT, ResponseT> {
    private final BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor;
    private BundlingSettings bundlingSettings;


    /**
     * Constructs an instance of BundlableApiCallableBuilder.
     *
     * @param grpcMethodDescriptor A method descriptor obtained from the generated GRPC
     * class.
     * @param bundlingDescriptor An object which splits and merges requests for the
     * purpose of bundling.
     */
    public BundlableApiCallableBuilder(
        MethodDescriptor<RequestT, ResponseT> grpcMethodDescriptor,
        BundlingDescriptor<RequestT, ResponseT> bundlingDescriptor) {
      super(grpcMethodDescriptor);
      this.bundlingDescriptor = bundlingDescriptor;
      this.bundlingSettings = null;
    }

    /**
     * Provides the bundling settings to use.
     */
    public BundlableApiCallableBuilder<RequestT, ResponseT> setBundlingSettings(
        BundlingSettings bundlingSettings) {
      this.bundlingSettings = bundlingSettings;
      return this;
    }

    /**
     * Returns the bundling settings that have been previously provided.
     */
    public BundlingSettings getBundlingSettings() {
      return bundlingSettings;
    }

    /**
     * Builds an ApiCallable which supports bundling, using the settings provided.
     *
     * @param serviceApiSettings Provides the channel and executor.
     */
    public BundlableApiCallableInfo<RequestT, ResponseT> buildBundlable(
        ServiceApiSettings serviceApiSettings) throws IOException {
      ApiCallable<RequestT, ResponseT> callable = build(serviceApiSettings);
      BundlerFactory<RequestT, ResponseT> bundlerFactory = null;
      if (bundlingSettings != null) {
        bundlerFactory = new BundlerFactory<>(bundlingDescriptor, bundlingSettings);
        callable = callable.bundling(bundlingDescriptor, bundlerFactory);
      }
      return BundlableApiCallableInfo.create(callable, bundlerFactory);
    }
  }

}
