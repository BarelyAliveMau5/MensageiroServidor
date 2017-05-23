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
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import mensageiro.socket.Mensagem;
/**
 * @author BarelyAliveMau5
 */
public class Servidor implements Runnable {
    private ThreadCliente clientes[];
    private boolean executando;
    
    private ServerSocket server = null;
    private Thread thread = null;
    public int numClientes = 0, 
               port = 13000;
    private BancoDeDados db;
    
    public static final int MAX_THREADS = 100;
    public static final String ARQUIVO_BD = "usuarios.xml";
    private static final String SERVIDOR = "SERVIDOR";
    private static final String TODOS = "";
    
    public Servidor(int threads) {
        if (threads > MAX_THREADS)
            threads = MAX_THREADS;
        clientes = new ThreadCliente[threads];
        db = new BancoDeDados(ARQUIVO_BD);
        try {
            server = new ServerSocket(port);
            port = server.getLocalPort();
            Logger.getGlobal().log(Level.INFO, "Servidor iniciado em {0}:{1}", 
                    new Object[] { InetAddress.getLocalHost(), server.getLocalPort() });
            iniciar();
        } catch (BindException ex) {
            Logger.getGlobal().log(Level.SEVERE, "Porta já usada, impossivel continuar. finalizando");
            System.exit(-1);
        } catch(IOException ex) {
            Logger.getGlobal().log(Level.WARNING, null, ex);
        } 
    }

    @Override
    public void run() {
        executando = true;
        while (executando) {
            try {
                Socket sock = server.accept();
                if (sock.isConnected()) {
                    Logger.getGlobal()
                            .log(Level.INFO, "Conexão aceita com cliente {0}",sock.getRemoteSocketAddress());
                    addThread(sock);
                }
            } catch (IOException ex) {
                Logger.getGlobal().log(Level.WARNING, "Erro ao aceitar conexão", ex);
            }
        }
    }
    
    private int acharCliente(int ID) {
        for (int i = 0; i < numClientes; i++) {
            if (clientes[i].getID() == ID) {
                return i;
            }
        }
        return 0;
    }
    
    public synchronized void remover(int ID) {
        int pos = acharCliente(ID);
        if (pos >= 0) {
            ThreadCliente toTerminate = clientes[pos];
            Logger.getGlobal().log(Level.INFO, "Tirando cliente {0}", ID);
            if (pos < numClientes-1) {
                for (int i = pos+1; i < numClientes; i++) {
                    clientes[i-1] = clientes[i];
                }
            }
            numClientes--;
            try {
                toTerminate.close();
            }
            catch(IOException ex) {
                Logger.getGlobal().log(Level.WARNING, "Erro fechando thread {0}", ex);
            }
            toTerminate.finish();
        }
    }
    
    public final void iniciar() {
        if (!executando) {
            Thread a = new Thread(this);
            a.start();
        } else {
            Logger.getGlobal().log(Level.WARNING, "Tentativa de iniciar Thread já iniciada");
        }
    }
    
    public synchronized void parar() { 
        for (int i = 0; i < MAX_THREADS; i++)
            try {
                if (clientes[i].conectado())
                    clientes[i].close();
            } catch (IOException ex) {
                Logger.getGlobal().log(Level.SEVERE, null, ex);
            }
        executando = false;
    }
    
    
    private void addThread(Socket socket) {
        if (numClientes < clientes.length) {
            clientes[numClientes] = new ThreadCliente(this, socket);
            try {
                clientes[numClientes].abrir_conexao();
                clientes[numClientes].start();
                numClientes++;
            }
            catch(IOException ex) {
                Logger.getGlobal().log(Level.SEVERE, null, ex);
            }
        }
        else {
            Logger.getGlobal()
                    .log(Level.WARNING, "Cliente recusado: limite de {0} usuarios atingido", clientes.length);
        }
    }
    
    private ThreadCliente acharUserThread(String usr) {
        for (int i = 0; i < numClientes; i++) {
            if (clientes[i].nome.equals(usr)) {
                return clientes[i];
            }
        }
        return null;
    }

    private void enviarMensagem(int idCliente, Mensagem.Tipos tipo, String remetente, String mensagem, 
                                String destinatario) {
        if (acharCliente(idCliente) > 0)
            clientes[acharCliente(idCliente)].enviar(new Mensagem(tipo, remetente, mensagem, destinatario));
        else
            Logger.getGlobal().log(Level.WARNING, "Falha ao enviar mensagem ao cliente {0}", idCliente);
    }
    
    private void lidarLogin(Mensagem msg, int ID) {
        if (acharUserThread(msg.remetente) == null) {
            // add: verificar se a senha é nula (login convidado)
            if (db.checarLogin(msg.remetente, msg.conteudo) || msg.conteudo.equals("")) {
                    clientes[acharCliente(ID)].nome = msg.remetente;
                    enviarMensagem(ID, Mensagem.Tipos.LOGIN, SERVIDOR, Mensagem.Resp.LOGIN_OK, msg.remetente);
                    //clientes[acharCliente(ID)].send(
                    //       new Mensagem(Mensagem.Tipos.LOGIN, SERVIDOR, "TRUE", msg.remetente)
                    //);
                    enviarParaTodos(Mensagem.Tipos.ANUNCIAR_NOVO_USUARIO, SERVIDOR, msg.remetente);
                    enviarListaUsuarios(msg.remetente);
            } else {
                clientes[acharCliente(ID)].enviar(
                        new Mensagem(Mensagem.Tipos.LOGIN, SERVIDOR, "FALSE", msg.remetente)
                );
            }
            
        } else {
            clientes[acharCliente(ID)].enviar(
                    new Mensagem(Mensagem.Tipos.LOGIN, SERVIDOR, "FALSE", msg.remetente)
            );
            Logger.getGlobal().log(Level.WARNING, "Cliente tentando fazer login já estando logado");
        }
    }
    
    private void enviarParaTodos(Mensagem.Tipos tipo,String remetente, String msg) {
        Mensagem a_enviar = new Mensagem(tipo, remetente, msg, TODOS);
        for (int i = 0; i < numClientes; i++) {
            clientes[i].enviar(a_enviar);
        }
    }
    
    private void enviarMsgEntradaUsuario(String nome) {
        
    }
    
    private void enviarMsgSaidaUsuario(String nome) {
        
    }
    
    private void enviarListaUsuarios(String destino) {
        for (int i = 0; i <numClientes; i++) {
            acharUserThread(destino).enviar(
                    new Mensagem(Mensagem.Tipos.ANUNCIAR_NOVO_USUARIO, "SERVER", clientes[i].nome, destino)
            );
        }
    }
    
    public void lidar(int ID, Mensagem msg) {
        switch (msg.tipo()) {
            case MENSAGEM:
                break;
            case LOGIN:
                lidarLogin(msg, ID);
                break;
            case LOGOUT:
                break;
            case PEDIR_TRANSFERENCIA:
                break;
            case TRANSFERIR:
                break;
            case REGISTRAR_USUARIO:
                break;
            case TESTE:
                remover(ID);
                break;
            case RESULT_NOVO_USUARIO:    // não deve ser enviado pro servidor
            case ANUNCIAR_NOVO_USUARIO:  // idem
            case ANUNCIAR_SAIDA_USUARIO: // idem
            default:
                // não seria ao acaso que isso aconteceria, seja la quem fez isso não deve permanecer
                Logger.getGlobal().log(Level.SEVERE, "Tipo de mensagem não suportado");
                remover(ID);  
                break;
        }
    }
}
