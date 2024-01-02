/*
 * Installer
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.installer.actions;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;

public interface ProgressCallback
{
    enum MessagePriority
    {
        LOW, NORMAL,
        /**
         * Unused so far
         */
        HIGH,
    }
    
    default void start(String label)
    {
        message(label);
    }

    /**
     * Start a new step.
     */
    default void stage(String message, boolean withProgress)
    {
        message(message);
    }

    /**
     * Start a new step with indefinite progress.
     */
    default void stage(String message)
    {
        stage(message, false);
    }

    /**
     * @see #message(String, MessagePriority)
     */
    default void message(String message)
    {
        message(message, MessagePriority.NORMAL);
    }

    /**
     * Does not affect indeterminacy or progress, just updates the text (or prints
     * it)
     */
    void message(String message, MessagePriority priority);

    default void progress(double progress)
    {
        //TODO: Better bar? We're in console.. so let not spam with updates
        //System.out.println(DecimalFormat.getPercentInstance().format(progress));
    }

    default ProgressBar getStepProgress() {
        return ProgressBar.NOOP;
    }

    default InputStream wrapStepDownload(URLConnection connection) throws IOException {
        connection.connect();
        getStepProgress().setMaxProgress(connection.getContentLength());
        return wrapStepDownload(connection.getInputStream());
    }

    default InputStream wrapStepDownload(InputStream in) {
        return new FilterInputStream(in) {
            private int nread = 0;

            @Override
            public int read() throws IOException {
                int c = in.read();
                if (c >= 0) getStepProgress().progress(++nread);
                return c;
            }


            @Override
            public int read(byte[] b) throws IOException {
                int nr = in.read(b);
                if (nr > 0) getStepProgress().progress(nread += nr);
                return nr;
            }


            @Override
            public int read(byte[] b,
                            int off,
                            int len) throws IOException {
                int nr = in.read(b, off, len);
                if (nr > 0) getStepProgress().progress(nread += nr);
                return nr;
            }


            @Override
            public long skip(long n) throws IOException {
                long nr = in.skip(n);
                if (nr > 0) getStepProgress().progress(nread += nr);
                return nr;
            }
        };
    }

    static ProgressCallback TO_STD_OUT = new ProgressCallback() {

        @Override
        public void message(String message, MessagePriority priority)
        {
            System.out.println(message);
        }
    };
    
    static ProgressCallback withOutputs(OutputStream... streams)
    {
        return new ProgressCallback()
        {
            @Override
            public void message(String message, MessagePriority priority)
            {
                message = message + System.lineSeparator();
                for (OutputStream out : streams)
                {
                    try
                    {
                        out.write(message.getBytes());
                        out.flush();
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }

    interface ProgressBar {
        ProgressBar NOOP = new ProgressBar() {
            @Override
            public void setMaxProgress(int maximum) {

            }

            @Override
            public void progress(int value) {

            }

            @Override
            public void percentageProgress(double value) {

            }

            @Override
            public void setIndeterminate(boolean indeterminate) {

            }
        };

        void setMaxProgress(int maximum);
        void progress(int value);
        void percentageProgress(double value);
        void setIndeterminate(boolean indeterminate);
    }
}
