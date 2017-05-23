package mensageiro.servidor;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

public class BancoDeDados {

    private String caminhoDoArquivo;
    private static final String STR_USUARIO = "usuario", 
                                STR_SENHA = "senha",
                                STR_NOME = "nome";

    public String caminhoDoArquivo() {
        return caminhoDoArquivo;
    }

    public void setArquivo(String caminho) {
        this.caminhoDoArquivo = caminho;
    }

    public BancoDeDados(String caminho) {
        this.caminhoDoArquivo = caminho;
    }
    
    public boolean usuarioExistente(String nome) {

        try {
            File fXmlFile = new File(caminhoDoArquivo);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName(STR_USUARIO);

            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    if(getTagValue(STR_NOME, eElement).equals(nome)) {
                        return true;
                    }
                }
            }
        } catch(IOException | ParserConfigurationException | SAXException ex) {
            Logger.getLogger(BancoDeDados.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    public boolean checarLogin(String nome, String senha) {

        if(!usuarioExistente(nome))
            return false;

        try {
            File fXmlFile = new File(this.caminhoDoArquivo);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName(STR_USUARIO);

            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    if (getTagValue(STR_NOME, eElement).equals(nome)
                            && getTagValue(STR_SENHA, eElement).equals(senha)) {
                        return true;
                    }
                }
            }
            throw new Exception("Usuario existe mas não pode ser verificado");
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            Logger.getLogger(BancoDeDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(BancoDeDados.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    public void novoUsuario(String nome, String senha) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(this.caminhoDoArquivo);

            Node dado = doc.getFirstChild();

            Element novoUsuario = doc.createElement(STR_USUARIO);
            
            Element novoNome = doc.createElement(STR_NOME);
            novoNome.setTextContent(nome);
            Element novaSenha = doc.createElement(STR_SENHA);
            novaSenha.setTextContent(senha);

            novoUsuario.appendChild(novoNome);
            novoUsuario.appendChild(novaSenha);
            dado.appendChild(novoUsuario);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(this.caminhoDoArquivo));
            transformer.transform(source, result);

        } catch(IOException | ParserConfigurationException | TransformerException | 
                DOMException | SAXException ex) {
            Logger.getLogger(BancoDeDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static String getTagValue(String sTag, Element eElement) {
        NodeList nlList = eElement.getElementsByTagName(sTag).item(0).getChildNodes();
        Node nValue = (Node) nlList.item(0);
        return nValue.getNodeValue();
    }
}
