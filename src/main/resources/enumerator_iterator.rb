###################################################################################
#                                                                                 #
#                   Copyright 2010-2013 Ning, Inc.                                #
#                                                                                 #
#      Ning licenses this file to you under the Apache License, version 2.0       #
#      (the "License"); you may not use this file except in compliance with the   #
#      License.  You may obtain a copy of the License at:                         #
#                                                                                 #
#          http://www.apache.org/licenses/LICENSE-2.0                             #
#                                                                                 #
#      Unless required by applicable law or agreed to in writing, software        #
#      distributed under the License is distributed on an "AS IS" BASIS, WITHOUT  #
#      WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the  #
#      License for the specific language governing permissions and limitations    #
#      under the License.                                                         #
#                                                                                 #
###################################################################################


#
#                       DO NOT EDIT!!!
#    File automatically generated by killbill-java-parser (git@github.com:killbill/killbill-java-parser.git)
#


module Killbill
  module Plugin
    module Model

      java_package 'java.util'

      include java.util.Iterator
      class EnumeratorIterator

        def initialize(delegate)
          @buffer = []
          # We expect an Enumerable or Enumerator
          @delegate = delegate.is_a?(Enumerable) ? delegate.to_enum : delegate
          _next
        end

        def has_next
          !@next.nil?
        end
        
        alias_method :hasNext, :has_next

        def next
          prev = @next
          _next
          prev.to_java
        end

        def remove
          raise NotImplementedError.new
        end

        private

        def _next
          @next = @buffer.shift
          return unless @next.nil?

          @next = @delegate.next rescue nil
          return if @next.nil?

          if @next.is_a? Enumerable
            @buffer += @next.to_a
          else
            @buffer << @next
          end
          _next
        end

      end
    end
  end
end
