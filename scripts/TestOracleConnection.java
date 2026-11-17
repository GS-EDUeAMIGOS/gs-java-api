import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Utilitario somente leitura para validar a conectividade JDBC com o Oracle da FIAP
 * utilizando exclusivamente variaveis de ambiente.
 */
public class TestOracleConnection {

    public static void main(String[] args) {
        String url = requireEnv("ORACLE_DB_URL", "jdbc:oracle:thin:@oracle.fiap.com.br:1521:ORCL");
        String username = requireEnv("ORACLE_DB_USERNAME", null);
        String password = requireEnv("ORACLE_DB_PASSWORD", null);

        System.out.println("Testando conexao JDBC Oracle...");
        System.out.println("URL     : " + url);
        System.out.println("Usuario : " + username);

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 'OK' AS STATUS, USER AS USUARIO_LOGADO, SYSDATE AS DATA_SERVIDOR FROM DUAL");
             ResultSet rs = statement.executeQuery()) {

            if (rs.next()) {
                System.out.println("[SUCESSO] Conexao estabelecida com o Oracle FIAP!");
                System.out.println("Status        : " + rs.getString("STATUS"));
                System.out.println("Usuario logado: " + rs.getString("USUARIO_LOGADO"));
                System.out.println("Data servidor : " + rs.getTimestamp("DATA_SERVIDOR"));
            }
        } catch (SQLException e) {
            System.err.println("[ERRO] Falha ao conectar no Oracle:");
            System.err.println("SQLState : " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            System.err.println("Mensagem : " + e.getMessage());
            System.exit(1);
        }
    }

    private static String requireEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            if (defaultValue != null) {
                return defaultValue;
            }
            System.err.println("[ERRO] Variavel de ambiente obrigatoria nao definida: " + key);
            System.exit(2);
        }
        return value;
    }
}
