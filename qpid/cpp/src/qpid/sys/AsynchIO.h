#ifndef _sys_AsynchIO
#define _sys_AsynchIO
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include "Dispatcher.h"

#include <boost/function.hpp>
#include <deque>

namespace qpid {
namespace sys {

/*
 * Asynchronous acceptor: accepts connections then does a callback with the
 * accepted fd
 */
class AsynchAcceptor {
public:
    typedef boost::function1<void, int> Callback;

private:
    Callback acceptedCallback;
    DispatchHandle handle;

public:
    AsynchAcceptor(int fd, Callback callback);
    void start(Poller::shared_ptr poller);

private:
    void readable(DispatchHandle& handle);
};

/*
 * Asycnchronous reader/writer: 
 * Reader accepts buffers to read into; reads into the provided buffers
 * and then does a callback with the buffer and amount read. Optionally it can callback
 * when there is something to read but no buffer to read it into.
 * 
 * Writer accepts a buffer and queues it for writing; can also be given
 * a callback for when writing is "idle" (ie fd is writable, but nothing to write)
 * 
 * The class is implemented in terms of DispatchHandle to allow it to be deleted by deleting
 * the contained DispatchHandle
 */
class AsynchIO : private DispatchHandle {
public:
    struct Buffer {
        typedef boost::function1<void, const Buffer&> RecycleStorage;
        
        char* const bytes;
        const int32_t byteCount;
        int32_t dataStart;
        int32_t dataCount;
        
        Buffer(char* const b, const int32_t s) :
            bytes(b),
            byteCount(s),
            dataStart(0),
            dataCount(s)
        {}

        virtual ~Buffer()
        {}
    };

    typedef boost::function2<void, AsynchIO&, Buffer*> ReadCallback;
    typedef boost::function1<void, AsynchIO&> EofCallback;
    typedef boost::function1<void, AsynchIO&> DisconnectCallback;
    typedef boost::function1<void, AsynchIO&> BuffersEmptyCallback;
    typedef boost::function1<void, AsynchIO&> IdleCallback;

private:
    ReadCallback readCallback;
    EofCallback eofCallback;
    DisconnectCallback disCallback;
    BuffersEmptyCallback emptyCallback;
    IdleCallback idleCallback;
    std::deque<Buffer*> bufferQueue;
    std::deque<Buffer*> writeQueue;

public:
    AsynchIO(int fd,
        ReadCallback rCb, EofCallback eofCb, DisconnectCallback disCb,
        BuffersEmptyCallback eCb = 0, IdleCallback iCb = 0);
    void queueForDeletion();

    void start(Poller::shared_ptr poller);
    void queueReadBuffer(Buffer* buff);
    void queueWrite(Buffer* buff);

private:
    ~AsynchIO();
    void readable(DispatchHandle& handle);
    void writeable(DispatchHandle& handle);
    void disconnected(DispatchHandle& handle);
};

}}

#endif // _sys_AsynchIO
