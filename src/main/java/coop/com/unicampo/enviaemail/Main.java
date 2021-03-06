/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package coop.com.unicampo.enviaemail;

import coop.com.unicampo.enviaemail.Controller.ConfiguracoesController;
import coop.com.unicampo.enviaemail.Controller.EmailController;
import coop.com.unicampo.enviaemail.Converter.EmailConverter;
import coop.com.unicampo.enviaemail.model.Configuracoes;
import coop.com.unicampo.enviaemail.model.Email;
import coop.com.unicampo.enviaemail.model.EntityManagerDAO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.persistence.EntityManager;
import org.apache.log4j.Logger;

/**
 *
 * @author Franciscato
 */
public class Main {

    private static Logger log = Logger.getLogger(Main.class);

    public static EntityManager em;
    private static String host;
    private static Integer porta;
    private static Map<String, String> to;
    private static Map<String, String> cc;
    private static Map<String, String> cco;
    private static List<String> anexo;
    private static String from;
    private static String password;
    private static String subject;
    private static String body;

    public static void main(String[] args) throws IOException {

        Timer timer = null;
        if (timer == null) {
            timer = new Timer();
            TimerTask tarefa;
            tarefa = new TimerTask() {

                @Override
                public void run() {

                    List<Email> emails = new ArrayList<>();

                    em = EntityManagerDAO.getEntityManager();
                    emails.addAll(EmailController.getEmailsNotSend());

                    for (Email email : emails) {
                        enviaEmail(email.getId(), email.getCodigoConfiguracao());
                    }

                }
            };
            timer.scheduleAtFixedRate(tarefa, 5000, 5000);
        }
    }

    public static void enviaEmail(Integer id, Integer codigoConfiguracao) {
        to = null;
        cc = null;
        cco = null;
        anexo = null;
        from = null;
        password = null;
        subject = null;
        body = null;
        
        try {
            log.info("Buscando Configurações.");
            Configuracoes config;

            if (codigoConfiguracao == null) {
                config = ConfiguracoesController.getConfiguracaoAtiva();
            } else {
                config = ConfiguracoesController.getConfiguracaoPerID(codigoConfiguracao);
            }

            host = config.getHost();
            porta = config.getPorta();

            log.info("Buscando Email.");
            Email email = EmailController.getEmailPerID(id);
            to = EmailConverter.stringToMap(email.getTo(), ";");
            if (email.getCc() != null) {
                cc = EmailConverter.stringToMap(email.getCc(), ";");
            }
            if (email.getCco() != null) {
                cco = EmailConverter.stringToMap(email.getCco(), ";");
            }
            if (email.getAnexo() != null) {
                anexo = EmailConverter.stringToList(email.getAnexo(), ";");
            }
            from = email.getFrom();
            password = email.getPassword();
            subject = email.getSubject();
            body = email.getBody();

            log.info("Configurando SMTP");
            Properties properties = System.getProperties();
            properties.put("mail.smtp.host", host);
            properties.put("mail.smtp.port", porta);
            properties.put("mail.smtp.ssl.enable", ("SSL".equals(config.getDescricaoDaConexao())));
            properties.put("mail.smtp.starttls.enable", ("TLS".equals(config.getDescricaoDaConexao())));
            properties.put("mail.smtp.auth", (!"NENHUM".equals(config.getDescricaoDaConexao())));

            properties.put("mail.smtp.ssl.trust", "*");

            Session session = null;
            Authenticator authenticator = null;
            if ((boolean) properties.get("mail.smtp.auth") == true) {
                authenticator = new Authenticator() {
                    private PasswordAuthentication pa = new PasswordAuthentication(from, password);

                    @Override
                    public PasswordAuthentication getPasswordAuthentication() {
                        return pa;
                    }
                };
                session = Session.getDefaultInstance(properties, authenticator);
            } else {
                session = Session.getDefaultInstance(properties);
            }

            log.info("Montando Mensagem: " + email.getId());
            MimeMessage message = new MimeMessage(session);

            message.setFrom(new InternetAddress(from));
            message.setSubject(subject);

            //To            
            for (Map.Entry<String, String> map : to.entrySet()) {
                message.addRecipient(Message.RecipientType.TO,
                        new InternetAddress(map.getKey(), map.getValue())
                );
            }

            //CC
            if (cc != null) {
                for (Map.Entry<String, String> map : cc.entrySet()) {
                    message.addRecipient(Message.RecipientType.CC,
                            new InternetAddress(map.getKey(), map.getValue())
                    );
                }
            }

            //CCO                        
            if (cco != null) {
                for (Map.Entry<String, String> map : cco.entrySet()) {
                    message.addRecipient(Message.RecipientType.BCC,
                            new InternetAddress(map.getKey(), map.getValue())
                    );
                }
            }

            //Anexo
            Multipart mp = new MimeMultipart();
            if (anexo != null) {
                for (int index = 0; index < anexo.size(); index++) {
                    MimeBodyPart mbp = new MimeBodyPart();
                    FileDataSource fds = new FileDataSource(anexo.get(index));
                    mbp.setDataHandler(new DataHandler(fds));
                    mbp.setFileName(fds.getName());

                    mp.addBodyPart(mbp, index);
                }
            }

            // Corpo
            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(body, "text/html");
            mp.addBodyPart(bodyPart);
            message.setContent(mp);

            log.info("Enviando: " + email.getId());
            try {
                Transport.send(message);
                EmailController.updateEmail(email, 1, null);
                log.info("Mensagem Enviada: " + email.getId());
            } catch (MessagingException mex) {
                EmailController.updateEmail(email, 0, mex.getMessage());
                log.info("Mensagem NÃO Enviada: " + email.getId());
            }

        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            log.error("HELP: call instructions:"
                    + "java -jar [ProjectHome]/EnviaEmail.jar [Email.id[Integer] Configuracoes.id[Integer]]");
            ex.printStackTrace();
        } catch (MessagingException mex) {
            log.error(mex);
            mex.printStackTrace();
        } catch (Exception ex) {
            log.error(ex);
            ex.printStackTrace();
        }

    }
}
