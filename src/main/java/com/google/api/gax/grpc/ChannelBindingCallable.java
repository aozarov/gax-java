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

import io.grpc.Channel;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * {@code ChannelBindingCallable} is a {@link FutureCallable} with a bound {@link io.grpc.Channel}.
 *
 * If the {@link #futureCall(CallContext)} is called with a null {@code Channel},
 * {@code ChannelBindingCallable} calls {@code futureCall} of the underlying {@code FutureCallable}
 * with the bound {@code Channel} instead.
 * Otherwise, the {@code CallContext} is directly forwarded to the underlying
 * {@code FutureCallable::futureCall}.
 */
class ChannelBindingCallable<RequestT, ResponseT> implements FutureCallable<RequestT, ResponseT> {
  private final FutureCallable<RequestT, ResponseT> callable;
  private final Channel channel;

  ChannelBindingCallable(FutureCallable<RequestT, ResponseT> callable, Channel channel) {
    this.callable = Preconditions.checkNotNull(callable);
    this.channel = Preconditions.checkNotNull(channel);
  }

  @Override
  public ListenableFuture<ResponseT> futureCall(CallContext<RequestT> context) {
    if (context.getChannel() == null) {
      context = context.withChannel(channel);
    }
    return callable.futureCall(context);
  }

  @Override
  public String toString() {
    return String.format("bind-channel(%s)", callable);
  }
}
