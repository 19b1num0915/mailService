package Data;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Html {

    private Long id;
    private String name;
    private String html;

    public  Html(){

    }

    public Html(Long id, String name, String html) {
        this.id = id;
        this.name = name;
        this.html = html;
    }


    public static Html from(Row row){
        Html html = new Html();
        html.setId(row.getLong("id"));
        html.setName(row.getString("names"));
        html.setHtml(row.getString("html"));
        return html;
    }

    public static Multi<Html> findAll(PgPool client) {
        return client.query("SELECT * FROM templates").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Html::from);
    }


    public static Uni<Html> findById(PgPool client, Long id) {
        return client.query("SELECT * from templates where id="+ id).execute()
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from((Row) iterator.next()) : null);
    }


    public static Uni<Boolean> delete(PgPool client, Long id) {
        return client.query("DELETE FROM templates WHERE id =" + id).execute()
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
