package org.acme;


import Data.Html;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import static Utils.Globals.isValidEmail;


@Path("/email")
public class MailResource {

    // PBVEK-BYCSD
    // wysiwyg
    public static final Logger logger = Logger.getLogger(MailResource.class);
    @Inject
    Mailer mailer;

    @Inject
    PgPool dbclient;

    @GET
    @Path("html/getAll")
    public Multi<Html> getHtml(){
        return Html.findAll(dbclient);
    }

    @GET
    @Path("/html/select")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getID(Long id){
        return Html.findById(dbclient, id)
                .onItem()
                .transform(idHtml -> idHtml != null ? Response.ok(idHtml)
                    : Response.status(RestResponse.Status.NOT_FOUND))
                .onItem().transform(Response.ResponseBuilder::build);
    }


    @DELETE
    @Path("/html/delete")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> deleteHtml(Long id) {
        return Html.delete(dbclient, id)
                .onItem()
                .transform(deleted -> deleted ? RestResponse.Status.NO_CONTENT : RestResponse.Status.NOT_FOUND)
                .onItem()
                .transform(status -> Response.status(status).build());
    }


    @POST
    @Path("/html/save")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public JsonObject savingHtml(JsonObject body){
        String name = body.getString("name");
        String html = body.getString("html");
        String msg = "none";

        try {
            dbclient.query("insert into templates(names, html) values('"+name+"','"+html+"')")
                    .execute()
                    .await().
                    indefinitely();

            msg = "success";

        } catch (Exception e){
            logger.error("error", e);
            msg = "failed";
        }
        return new JsonObject().put("msg", msg);
    }


    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/html/update")
    public JsonObject updateHtml(JsonObject body){
        Long id = body.getLong("id");
        String names = body.getString("name");
        String html = body.getString("html");
        String msg = "none";
        try {
            dbclient.query("update templates set html = '"+html+"' where id = '"+id+"' and names = '"+names+"'")
                    .execute()
                    .await()
                    .indefinitely();
            msg = "success";
        } catch (Exception e){
            logger.error("error", e);
            msg = "failed";
        }
        return new JsonObject().put("msg", msg);
    }

    @POST
    @Path("/send/multi")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public JsonObject sendMulti(JsonObject body) {
        final JsonObject jret1 = new JsonObject();
        String type_ = body.getJsonObject("body").getString("type");
        String html = "";
        if ("template".equalsIgnoreCase(type_)) {
            String templateName = body.getJsonObject("body").getString("templateName");

            // getTemplate
            dbclient.query("select HTML from Templates where names ='" + templateName + "'")
                    .execute()
                    .onItem()
                    .transformToUni(rowSet -> Uni.createFrom().item(rowSet.iterator().next()))
                    .onItem()
                    .transform(row -> jret1.put("html", row.getString(0)))
                    .await()
                    .indefinitely();

            html = jret1.getString("html");
        }
        JsonObject jret = new JsonObject();
        // add To
        JsonArray addr = body.getJsonArray("to");
        JsonArray a = new JsonArray();
        for (int i = 0; i < addr.size(); i++) {
            String from_email = "";
            String to_email = "";
            String msg = "none";
            try {
                Mail mail_ = new Mail();
                // set mail from
                from_email = body.getString("from");
                mail_.setFrom(from_email);
                to_email = addr.getJsonObject(i).getString("email");

                if (isValidEmail(to_email)) {
                    mail_.addTo(to_email);
                }
                // cc ?
                if (body.containsKey("cc")) {
                    JsonArray cc = body.getJsonArray("cc");
                    for (int j = 0; j < cc.size(); j++) {
                        if (isValidEmail(cc.getString(j))) {
                            mail_.addCc(cc.getString(j));
                        }
                    }
                }
                // bcc ?
                if (body.containsKey("bcc")) {
                    JsonArray bcc = body.getJsonArray("bcc");
                    for (int k = 0; k < bcc.size(); k++) {
                        if (isValidEmail(bcc.getString(k))) {
                            mail_.addBcc(bcc.getString(k));
                        }
                    }
                }

                String subject = body.getString("subject");

                // check subject length
                if (subject.length() > 255) {
                    throw new RuntimeException("Subject must be shorter than 255");
                }
                // set subject
                mail_.setSubject(subject);

                JsonArray attachments = body.getJsonArray("attachments");
                for (int q = 0; q < attachments.size(); q++) {
                    String fileName_ = attachments.getJsonObject(q).getString("name");
                    String fileType_ = attachments.getJsonObject(q).getString("type");
                    // base 64 orj irj baigaa
                    String data = attachments.getJsonObject(q).getString("data");
                    if (data == null){
                        throw new RuntimeException("attachment data is null");
                    }
                    byte[] fileData_ = Base64.getDecoder().decode(data);
                    if (fileData_ != null && fileName_ != null && fileType_ != null) {
                        // add attachments
                        mail_.addAttachment(fileName_, fileData_, fileType_);
                    }
                }

                // content type check!!!
                if ("text".equalsIgnoreCase(type_)) {
                    mail_.setText(body.getJsonObject("body").getString("content"));
                } else if ("html".equalsIgnoreCase(type_)) {
                    mail_.setHtml(body.getJsonObject("body").getString("content"));
                } else if ("template".equalsIgnoreCase(type_)) {
                    if (!html.isEmpty()) {
                        // template html replace
                        JsonObject o = addr.getJsonObject(i).getJsonObject("data");
                        for (String key : o.fieldNames()) {
                            html = html.replaceAll("\\{\\!" + key + "\\$\\}", o.getString(key));
                        }
                    }
                    mail_.setHtml(html);
                }
                // sending mail
                mailer.send(mail_);
                msg = "success";
            } catch (Exception e) {
                logger.error("", e);
                msg = "failed";
            }
            a.add(new JsonObject().put("email", to_email).put("msg", msg));
            final String email_from = from_email;
            final String email_to = to_email;
            final String msg_db = msg;
            CompletableFuture.runAsync(
                    () -> {
                        // log db save
                        dbclient.query("insert into logg(from1, to1, date1, msg) values('" + email_from + "' ,'" + email_to + "','" + LocalDate.now() + "', '"+msg_db+"')")
                                .execute()
                                .await()
                                .indefinitely();
                    });
        }
        jret.put("result", a);
        return jret;
    }


    @POST
    @Path("/send/single")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public JsonObject sendSingle(JsonObject body) {

        JsonArray to = body.getJsonArray("to");
        String type = body.getJsonObject("body").getString("type");

        // template
        JsonObject htmlJson = new JsonObject();
        String html="";

        // blackList
        JsonObject blackList = new JsonObject();
        JsonArray allBlackList = new JsonArray();

        // return
        JsonObject jret = new JsonObject();

        JsonArray a = new JsonArray();

        try {
            // template baival tuhain template name db select hiine
            if ("template".equalsIgnoreCase(type)) {
                String templateName = body.getJsonObject("body").getString("templateName");
                dbclient.query("select HTML from Templates where names ='" + templateName + "'")
                        .execute()
                        .onItem()
                        .transformToUni(rowSet -> Uni.createFrom().item(rowSet.iterator().next()))
                        .onItem()
                        .transform(row -> htmlJson.put("html", row.getString(0)))
                        .await()
                        .indefinitely();
                html = htmlJson.getString("html");
            }
        } catch (Exception e){
            logger.error("", e);
            throw new RuntimeException("Database deer "
                                        +body.getJsonObject("body").getString("templateName")+
                                        "gesen template alga baina");
        }

        try {
            // JsonArray luu black to dotor baigaa
            // emailuudig jsonObject bolgoj put hiij baigaa
            for (int bl = 0; bl < to.size(); bl++) {
                dbclient.query("select email from black_list where email = '" + to.getString(bl) + "' ")
                        .execute()
                        .onItem()
                        .transformToUni(rowSet -> Uni.createFrom().item(rowSet.iterator().next()))
                        .onItem()
                        .transform(row -> blackList.put("blackList", row.getString(0)))
                        .await()
                        .indefinitely();

                allBlackList.add(blackList);
                logger.infov("blackList={0}", blackList);
            }

        } catch (Exception e){
            logger.infov("", e);
        }


        // to gees Blacklisted baigaa mail remove hiij baina.
        for (int rm = 0; rm < to.size(); rm++){
            for (int i = 0; i < allBlackList.size(); i++){
                if(to.getString(rm).equalsIgnoreCase(allBlackList.getJsonObject(i).getString("blackList"))){
                    to.remove(rm);
                }
            }
        }




        // mail send
        if (to.size() > 0) {
          for (int i = 0; i < to.size(); i++) {
              String to1 = "";
              String from1 = "";
              String msg = "none";
              try {
                  Mail mail = new Mail();
                  from1 = body.getString("from");

                  to1 = to.getString(i);
                  mail.setFrom(body.getString("from"));

                  // check email
                  if (isValidEmail(to.getString(i))) {
                      mail.addTo(to.getString(i));
                  }

                  // cc ?
                  if (body.containsKey("cc")) {
                      JsonArray cc = body.getJsonArray("cc");
                      for (int j = 0; j < cc.size(); j++) {
                          if (isValidEmail(cc.getString(j))) {
                              mail.addCc(cc.getString(j));
                          }
                      }
                  }
                  // bcc ?
                  if (body.containsKey("bcc")) {
                      JsonArray bcc = body.getJsonArray("bcc");
                      for (int k = 0; k < bcc.size(); k++) {
                          if (isValidEmail(bcc.getString(k))) {
                              mail.addBcc(bcc.getString(k));
                          }
                      }
                  }

                  String subject = body.getString("subject");
                  if (subject.length() > 255) {
                      throw new RuntimeException("Subject must be shorter than 255");
                  }

                  JsonArray attachments = body.getJsonArray("attachments");
                  for (int q = 0; q < attachments.size(); q++) {
                      String fileName_ = attachments.getJsonObject(q).getString("name");
                      String fileType_ = attachments.getJsonObject(q).getString("type");
                      byte[] fileData_ = Base64.getDecoder().decode(attachments.getJsonObject(q).getString("data"));
                      if (fileData_ != null && fileName_ != null && fileType_ != null) {
                          // add attachments
                          mail.addAttachment(fileName_, fileData_, fileType_);
                      }
                  }

                  if ("text".equalsIgnoreCase(type)) {
                      mail.setText(body.getJsonObject("body").getString("content"));
                  } else if ("html".equalsIgnoreCase(type)) {
                      mail.setHtml(body.getJsonObject("body").getString("content"));
                  } else if ("template".equalsIgnoreCase(type)) {
                      if (!html.isEmpty()) {
                          // template html replace
                          JsonObject o = body.getJsonObject("body").getJsonObject("data");
                          for (String key : o.fieldNames()) {
                              html = html.replaceAll("\\{\\!" + key + "\\$\\}", o.getString(key));
                          }
                      }
                      mail.setHtml(html);
                  }
                  mailer.send(mail);
                  msg = "success";
              } catch (Exception e) {

                  logger.error("", e);
                  msg = "failed";
              }
              a.add(new JsonObject().put("email", to1).put("msg", msg));

              final String email_from = from1;
              final String email_to = to1;
              final String msg_db = msg;
              CompletableFuture.runAsync(
                      () -> {
                          // log db save
                          dbclient.query("insert into logg(from1, to1, date1, msg) values('" + email_from + "' ,'" + email_to + "','" + LocalDate.now() + "', '" + msg_db + "')")
                                  .execute()
                                  .await()
                                  .indefinitely();
                      });
          }
      }
        jret.put("result", a);
        return jret;
    }



    @POST
    @Path("blacklist/add")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject addBlackList(JsonObject body){
        JsonObject jret = new JsonObject();
        try{
            String mail = body.getString("mail");
            dbclient.query("call add_black_list ('"+mail+"')")
                    .execute()
                    .await()
                    .indefinitely();
            jret.put("msg", "success");
        } catch (Exception e){
            logger.error(e);
            jret.put("msg","failed");
        }
        return jret;
    }




}
