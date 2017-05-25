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
    private static final Logger LOGGER = Logger.getLogger(Servidor.class.getName());
    private ThreadCliente clientes[];
    private boolean executando;
    
    private ServerSocket server = null;
    public int numClientes = 0, 
               port = 13000;
    private BancoDeDados db;
    
    public static final int MAX_THREADS = 100;
    public static final String ARQUIVO_BD = "usuarios.xml";
    private static final String SERVIDOR = "SERVIDOR";
    private static final String TODOS = "";
    private static final String IGNORADO = "";
    
    public Servidor(int threads) {
        if (threads > MAX_THREADS)
            threads = MAX_THREADS;
        clientes = new ThreadCliente[threads];
        db = new BancoDeDados(ARQUIVO_BD);
        try {
            server = new ServerSocket(port);
            port = server.getLocalPort();
            LOGGER.log(Level.INFO, "Servidor iniciado em {0}:{1}", 
                       new Object[] { InetAddress.getLocalHost(), server.getLocalPort() });
            iniciar();
        } catch (BindException ex) {
            LOGGER.log(Level.SEVERE, "Porta já usada, impossivel continuar. finalizando");
            System.exit(-1);
        } catch(IOException ex) {
            LOGGER.log(Level.WARNING, null, ex);
        } 
    }

    @Override
    public void run() {
        executando = true;
        while (executando) {
            try {
                Socket sock = server.accept();
                if (sock.isConnected()) {
                    LOGGER.log(Level.INFO, "Conexão aceita com cliente {0}",sock.getRemoteSocketAddress());
                    addThread(sock);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Erro ao aceitar conexão", ex);
            }
        }
    }
    
    private int acharCliente(int ID) {
        for (int i = 0; i < numClientes; i++) {
            if (clientes[i].getID() == ID) {
                return i;
            }
        }
        return -1;
    }
    
    // TODO: usar ArrayList ao inves dessa gambiarra de reordenamento de um array simples
    public synchronized void remover(int ID) {
        int pos = acharCliente(ID);
        if (pos >= 0) {
            ThreadCliente toTerminate = clientes[pos];
            LOGGER.log(Level.INFO, "Tirando cliente {0}", ID);
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
                LOGGER.log(Level.WARNING, "Erro fechando thread {0}", ex);
            }
            toTerminate.finish();
        }
    }
    
    public final void iniciar() {
        if (!executando) {
            Thread a = new Thread(this);
            a.start();
        } else {
            LOGGER.log(Level.WARNING, "Tentativa de iniciar Thread já iniciada");
        }
    }
    
    public synchronized void parar() { 
        for (int i = 0; i < MAX_THREADS; i++)
            try {
                if (clientes[i].conectado())
                    clientes[i].close();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
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
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
        else {
            LOGGER.log(Level.WARNING, "Cliente recusado: limite de {0} usuarios atingido", clientes.length);
        }
    }
    
    private ThreadCliente acharThreadUsuario(String usuario) {
        for (int i = 0; i < numClientes; i++) {
            if (clientes[i].usuario.equals(usuario)) {
                return clientes[i];
            }
        }
        return null;
    }
    
    private ThreadCliente acharThreadUsuario(int ID) {
        for (int i = 0; i < numClientes; i++) {
            if (clientes[i].getID() == ID) {
                return clientes[i];
            }
        }
        return null;
    }

    private void enviarMensagem(int idDestino, Mensagem.Tipos tipo, String remetente, String mensagem, 
                                String destinatario) {
        if (acharCliente(idDestino) >= 0)
            clientes[acharCliente(idDestino)].enviar(new Mensagem(tipo, remetente, mensagem, destinatario));
        else
            LOGGER.log(Level.WARNING, "Falha ao enviar mensagem ao cliente {0}", idDestino);
    }

    private void enviarParaTodos(Mensagem.Tipos tipo,String remetente, String msg) {
        Mensagem a_enviar = new Mensagem(tipo, remetente, msg, TODOS);
        for (int i = 0; i < numClientes; i++) {
            clientes[i].enviar(a_enviar);
        }
    }

    private void anunciarEntradaUsuario(String remetente) {
        enviarParaTodos(Mensagem.Tipos.ANUNCIAR_LOGIN, SERVIDOR, remetente);
    }
    
    private void anunciarSaidaUsuario(String remetente) {
        enviarParaTodos(Mensagem.Tipos.ANUNCIAR_LOGOUT, SERVIDOR, remetente);
    }
    
    private void enviarListaUsuarios(String destino) {
        try {
            int idDestino = acharThreadUsuario(destino).getID();
            for (int i = 0; i <numClientes; i++) {
                enviarMensagem(idDestino, Mensagem.Tipos.LISTA_USUARIOS, SERVIDOR, clientes[i].usuario, destino);
            }
        } catch (NullPointerException ex) {
            LOGGER.log(Level.WARNING, "usuario não encontrado para enviar lista de usuarios", ex.toString());
        }
    }
        
    private void lidarLogin(int ID, Mensagem msg) {
        if (acharThreadUsuario(msg.remetente) == null) {
            if (db.checarLogin(msg.remetente, msg.conteudo) || msg.conteudo.equals("")) {
                    clientes[acharCliente(ID)].usuario = msg.remetente;
                    enviarMensagem(ID, Mensagem.Tipos.LOGIN, SERVIDOR, Mensagem.Resp.LOGIN_OK, msg.remetente);
                    anunciarEntradaUsuario(msg.remetente);
                    enviarListaUsuarios(msg.remetente);
                    return;
            }
        }
        enviarMensagem(ID, Mensagem.Tipos.LOGIN, SERVIDOR, Mensagem.Resp.LOGIN_FALHO, msg.remetente);
    }

    private void lidarLogout(int ID) {
        remover(ID);
        try {
            anunciarSaidaUsuario(acharThreadUsuario(ID).usuario);
        } catch (NullPointerException ex) {
            LOGGER.log(Level.WARNING, "usuario não encontrado para anunciar logout", ex.toString());
        }
    }
    
    private void lidarRespTransf(int ID, Mensagem msg) {
        try {
            String usuario = acharThreadUsuario(ID).usuario;
            int idDestino = acharThreadUsuario(msg.destinatario).getID();
            if (msg.destinatario.equals("") ||  msg.destinatario.equals(SERVIDOR) || 
                    acharThreadUsuario(msg.destinatario) == null)
                LOGGER.warning("transferencia com destinatario invalido");
            else {
                if (msg.conteudo.equals(Mensagem.Resp.TRANSFERENCIA_OK)); {
                    // problema: só funciona em rede interna.
                    String IP = acharThreadUsuario(msg.remetente).socket.getInetAddress().getHostAddress();
                    enviarMensagem(idDestino, msg.tipo(), usuario, IP, msg.destinatario);
                }
            }
        } catch (NullPointerException ex) {
            LOGGER.warning("usuario não encontrado");
        }
    }
    
    private void lidarPedidoTransf(int ID, Mensagem msg) {
        try {
            String usuario = acharThreadUsuario(ID).usuario;
            int idDestino = acharThreadUsuario(msg.destinatario).getID();
            if (msg.destinatario.equals("") ||  msg.destinatario.equals(SERVIDOR) || 
                    acharThreadUsuario(msg.destinatario) == null)
                LOGGER.warning("transferencia com destinatario invalido");
            else
                enviarMensagem(idDestino, msg.tipo(), usuario, "", msg.destinatario);
        } catch (NullPointerException ex) {
            LOGGER.warning("usuario não encontrado");
        }
    }
    
    private void lidarMensagem(int ID, Mensagem msg) {
        try {
            String usuario = acharThreadUsuario(ID).usuario;
            if (msg.destinatario.equals(TODOS)) {
                enviarParaTodos(msg.tipo(), usuario, TODOS);
            } else {
                int idDestino = acharThreadUsuario(msg.destinatario).getID();
                enviarMensagem(idDestino, msg.tipo(), usuario, msg.destinatario, msg.conteudo);
            }
        } catch (NullPointerException ex) {
            LOGGER.warning("usuario não encontrado");
        }
    }
    
    // chamado pela thread do cliente
    public void lidar(int ID, Mensagem msg) {
        switch (msg.tipo()) {
            case MENSAGEM:
                lidarMensagem(ID, msg);
                break;
            case LOGIN:
                lidarLogin(ID, msg);
                break;
            case LOGOUT:
                lidarLogout(ID);
                break;
            case PEDIR_TRANSFERENCIA:
                lidarPedidoTransf(ID, msg);
                break;
            case RESP_TRANSFERENCIA:
                lidarRespTransf(ID, msg);
                break;
            case REGISTRAR_USUARIO:
                break;
            case TESTE:
                remover(ID);
                break;
            case LISTA_USUARIOS:
            case ANUNCIAR_LOGIN:
            case ANUNCIAR_LOGOUT:
            default:
                LOGGER.log(Level.SEVERE, "Tipo de mensagem não suportada");
                remover(ID);  // desconectar seja lá quem tenha feito esse cliente customizado
                break;
        }
    }
}
