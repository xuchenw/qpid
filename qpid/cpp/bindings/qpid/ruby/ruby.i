/*
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
 */

%module cqpid
/* Ruby doesn't have a != operator*/
#pragma SWIG nowarn=378
%include "std_string.i"
%include "qpid/swig_ruby_typemaps.i"

/* Define the general-purpose exception handling */
%exception {
    try {
        $action
    }
    catch (qpid::messaging::MessagingException& mex) {
        static VALUE merror = rb_define_class("MessagingError", rb_eStandardError);
        rb_raise(merror, mex.what());
    }
}

%include "qpid/qpid.i"

