/*
 * The MIT License
 *
 * Copyright 2017 BarelyAliveMau5.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package mensageiro.servidor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import mensageiro.socket.Mensagem;

/**
 * @author BarelyAliveMau5
 */
public class ThreadCliente extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ThreadCliente.class.getName());
    private static int idIncremental;  // desambiguador
    private Servidor server = null;
    public Socket socket = null;
    private int ID = -1;
    public String usuario = "";
    private ObjectInputStream entrada  =  null;
    private ObjectOutputStream saida = null;
    private boolean executando = true;

    public ThreadCliente(Servidor server, Socket socket) {
        super();
        this.server = server;
        this.socket = socket;
        ID = ++idIncremental;  // forma simplificada de: idIncremental += 1; ID = idIncremental;
    }

    public void enviar(Mensagem msg) {
        try {
            saida.writeObject(msg);
            saida.flush();
        }
        catch (IOException ex) {
            LOGGER.severe(ex.toString());
        }
    }
    
    public int getID() {
        return ID;
    }

    public void finish() {
        executando = false;
    }

    @Override
    public void run() {
        LOGGER.log(Level.INFO, "Thread {0} executando", ID);
        executando = true;
        while (executando) {
            try {
                Mensagem msg = (Mensagem) entrada.readObject();
                server.lidar(ID, msg);
            }
            catch(IOException ex) {
                LOGGER.log(Level.WARNING, ex.toString());
                server.remover(ID);
                finish();
            } catch (ClassNotFoundException ex) {
                LOGGER.severe(ex.toString());
            }
        }
    }

    // chamado quando um cliente se conecta no servidor
    public void abrir_conexao() throws IOException {
        saida = new ObjectOutputStream(socket.getOutputStream());
        saida.flush();
        entrada = new ObjectInputStream(socket.getInputStream());
    }

    public boolean conectado() {
        return socket.isConnected();
    }
    
    public void close() throws IOException {
        if (socket != null)
            socket.close();

        if (entrada != null)
            entrada.close();

        if (saida != null)
            saida.close();
    }
    
}
