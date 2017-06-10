package servidor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import mensajeria.PaquetePersonaje;
import mensajeria.PaqueteUsuario;

/**
 * Clase que se encarga de las conexiones de usuarios. <br>
 */
public class Conector {
	/**
	 * Base de Datos. <br>
	 */
	private String url = "primeraBase.bd";
	/**
	 * Conector. <br>
	 */
	Connection connect;
	/**
	 * Cantidad máxima de objetos en el inventario. <br>
	 */
	private static final int CANTIDADMAXIMAITEMS = 12;

	/**
	 * Se conecta con la base de datos. En el caso de no poder conectarse,
	 * avisa. <br>
	 */
	public void connect() {
		try {
			Servidor.log.append("Estableciendo conexión con la base de datos..." + System.lineSeparator());
			connect = DriverManager.getConnection("jdbc:sqlite:" + url);
			Servidor.log.append("Conexión con la base de datos establecida con éxito." + System.lineSeparator());
		} catch (SQLException ex) {
			Servidor.log.append("Fallo al intentar establecer la conexión con la base de datos. " + ex.getMessage()
					+ System.lineSeparator());
		}
	}

	/**
	 * Cierra la base de datos. Avisa si no pudo cerrarla. <br>
	 */
	public void close() {
		try {
			connect.close();
		} catch (SQLException ex) {
			Servidor.log.append("Error al intentar cerrar la conexión con la base de datos." + System.lineSeparator());
			Logger.getLogger(Conector.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	/**
	 * Registra a un usuario en la base de datos. <br>
	 * 
	 * @param user
	 *            Usuario a registrar. <br>
	 * @return true si logra registrarlo, false de lo contrario. <br>
	 */
	public boolean registrarUsuario(PaqueteUsuario user) {
		ResultSet result = null;
		try {
			PreparedStatement st1 = connect.prepareStatement("SELECT * FROM registro WHERE usuario= ? ");
			st1.setString(1, user.getUsername());
			result = st1.executeQuery();
			if (!result.next()) {
				PreparedStatement st = connect
						.prepareStatement("INSERT INTO registro (usuario, password, idPersonaje) VALUES (?,?,?)");
				st.setString(1, user.getUsername());
				st.setString(2, user.getPassword());
				st.setInt(3, user.getIdPj());
				st.execute();
				Servidor.log.append("El usuario " + user.getUsername() + " se ha registrado." + System.lineSeparator());
				return true;
			} else {
				Servidor.log.append(
						"El usuario " + user.getUsername() + " ya se encuentra en uso." + System.lineSeparator());
				return false;
			}
		} catch (SQLException ex) {
			Servidor.log.append("Eror al intentar registrar el usuario " + user.getUsername() + System.lineSeparator());
			System.err.println(ex.getMessage());
			return false;
		}
	}

	/**
	 * Registra a un personaje en el juego. <br>
	 * 
	 * @param paquetePersonaje
	 *            Personaje a registrar. <br>
	 * @param paqueteUsuario
	 *            Cliente que usa al personaje. <br>
	 * @return true si lo registra, false de lo contrario. <br>
	 */
	public boolean registrarPersonaje(PaquetePersonaje paquetePersonaje, PaqueteUsuario paqueteUsuario) {
		try {
			// Registro al personaje en la base de datos
			PreparedStatement stRegistrarPersonaje = connect.prepareStatement(
					"INSERT INTO personaje (idInventario, idMochila,casta,raza,fuerza,destreza,inteligencia,saludTope,energiaTope,nombre,experiencia,nivel,idAlianza) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
					PreparedStatement.RETURN_GENERATED_KEYS);
			stRegistrarPersonaje.setInt(1, -1);
			stRegistrarPersonaje.setInt(2, -1);
			stRegistrarPersonaje.setString(3, paquetePersonaje.getCasta());
			stRegistrarPersonaje.setString(4, paquetePersonaje.getRaza());
			stRegistrarPersonaje.setInt(5, paquetePersonaje.getFuerza());
			stRegistrarPersonaje.setInt(6, paquetePersonaje.getDestreza());
			stRegistrarPersonaje.setInt(7, paquetePersonaje.getInteligencia());
			stRegistrarPersonaje.setInt(8, paquetePersonaje.getSaludTope());
			stRegistrarPersonaje.setInt(9, paquetePersonaje.getEnergiaTope());
			stRegistrarPersonaje.setString(10, paquetePersonaje.getNombre());
			stRegistrarPersonaje.setInt(11, 0);
			stRegistrarPersonaje.setInt(12, 1);
			stRegistrarPersonaje.setInt(13, -1);
			stRegistrarPersonaje.execute();
			// Recupero la última key generada
			ResultSet rs = stRegistrarPersonaje.getGeneratedKeys();
			if (rs != null && rs.next()) {
				// Obtengo el id
				int idPersonaje = rs.getInt(1);
				// Le asigno el id al paquete personaje que voy a devolver
				paquetePersonaje.setId(idPersonaje);
				// Le asigno el personaje al usuario
				PreparedStatement stAsignarPersonaje = connect
						.prepareStatement("UPDATE registro SET idPersonaje=? WHERE usuario=? AND password=?");
				stAsignarPersonaje.setInt(1, idPersonaje);
				stAsignarPersonaje.setString(2, paqueteUsuario.getUsername());
				stAsignarPersonaje.setString(3, paqueteUsuario.getPassword());
				stAsignarPersonaje.execute();
				// Por ultimo registro el inventario y la mochila
				if (this.registrarInventarioMochila(idPersonaje)) {
					Servidor.log.append("El usuario " + paqueteUsuario.getUsername() + " ha creado el personaje "
							+ paquetePersonaje.getId() + System.lineSeparator());
					return true;
				} else {
					Servidor.log.append(
							"Error al registrar la mochila y el inventario del usuario " + paqueteUsuario.getUsername()
									+ " con el personaje" + paquetePersonaje.getId() + System.lineSeparator());
					return false;
				}
			}
			return false;
		} catch (SQLException e) {
			Servidor.log.append(
					"Error al intentar crear el personaje " + paquetePersonaje.getNombre() + System.lineSeparator());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Crea el inventario del personaje. <br>
	 * 
	 * @param idInventarioMochila
	 *            ID del inventario del personaje. <br>
	 * @return true si lo crea, false de lo contrario. <br>
	 */
	public boolean registrarInventarioMochila(int idInventarioMochila) {
		try {
			// Preparo la consulta para el registro el inventario en la base de
			// datos
			PreparedStatement stRegistrarInventario = connect.prepareStatement(
					"INSERT INTO inventario(idInventario,manos1,manos2,pie,cabeza,pecho,accesorio) VALUES (?,-1,-1,-1,-1,-1,-1)");
			stRegistrarInventario.setInt(1, idInventarioMochila);
			// Preparo la consulta para el registro la mochila en la base de
			// datos
			PreparedStatement stRegistrarMochila = connect.prepareStatement(
					"INSERT INTO mochila(idMochila,item1,item2,item3,item4,item5,item6,item7,item8,item9,item10,item11,item12,item13,item14,item15,item16,item17,item18,item19,item20) VALUES(?,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1)");
			stRegistrarMochila.setInt(1, idInventarioMochila);
			// Registro inventario y mochila
			stRegistrarInventario.execute();
			stRegistrarMochila.execute();
			// Le asigno el inventario y la mochila al personaje
			PreparedStatement stAsignarPersonaje = connect
					.prepareStatement("UPDATE personaje SET idInventario=?, idMochila=? WHERE idPersonaje=?");
			stAsignarPersonaje.setInt(1, idInventarioMochila);
			stAsignarPersonaje.setInt(2, idInventarioMochila);
			stAsignarPersonaje.setInt(3, idInventarioMochila);
			stAsignarPersonaje.execute();
			Servidor.log.append("Se ha registrado el inventario de " + idInventarioMochila + System.lineSeparator());
			return true;
		} catch (SQLException e) {
			Servidor.log.append("Error al registrar el inventario de " + idInventarioMochila + System.lineSeparator());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Loguea al usuario. <br>
	 * 
	 * @param user
	 *            Usuario a ingresar. <br>
	 * @return true si lo logra, false de lo contrario. <br>
	 */
	public boolean loguearUsuario(PaqueteUsuario user) {
		ResultSet result = null;
		try {
			// Busco usuario y contraseña
			PreparedStatement st = connect
					.prepareStatement("SELECT * FROM registro WHERE usuario = ? AND password = ? ");
			st.setString(1, user.getUsername());
			st.setString(2, user.getPassword());
			result = st.executeQuery();
			// Si existe inicio sesion
			if (result.next()) {
				Servidor.log
						.append("El usuario " + user.getUsername() + " ha iniciado sesión." + System.lineSeparator());
				return true;
			}
			// Si no existe informo y devuelvo false
			Servidor.log.append("El usuario " + user.getUsername()
					+ " ha realizado un intento fallido de inicio de sesión." + System.lineSeparator());
			return false;
		} catch (SQLException e) {
			Servidor.log
					.append("El usuario " + user.getUsername() + " fallo al iniciar sesión." + System.lineSeparator());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Actualiza los datos del personaje. <br>
	 * 
	 * @param paquetePersonaje
	 *            Personaje. <br>
	 */
	public void actualizarPersonaje(PaquetePersonaje paquetePersonaje) {
		try {
			int i = 0;
			PreparedStatement stActualizarPersonaje = connect.prepareStatement(
					"UPDATE personaje SET fuerza=?, destreza=?, inteligencia=?, saludTope=?, energiaTope=?, experiencia=?, nivel=? "
							+ "  WHERE idPersonaje=?");
			stActualizarPersonaje.setInt(1, paquetePersonaje.getFuerza());
			stActualizarPersonaje.setInt(2, paquetePersonaje.getDestreza());
			stActualizarPersonaje.setInt(3, paquetePersonaje.getInteligencia());
			stActualizarPersonaje.setInt(4, paquetePersonaje.getSaludTope());
			stActualizarPersonaje.setInt(5, paquetePersonaje.getEnergiaTope());
			stActualizarPersonaje.setInt(6, paquetePersonaje.getExperiencia());
			stActualizarPersonaje.setInt(7, paquetePersonaje.getNivel());
			stActualizarPersonaje.setInt(8, paquetePersonaje.getId());
			stActualizarPersonaje.executeUpdate();
			Servidor.log.append("El personaje " + paquetePersonaje.getNombre() + " se ha actualizado con éxito."
					+ System.lineSeparator());
			if (paquetePersonaje.getCantidadObjetosInventario() != 0 && paquetePersonaje.nuevoItem()) {
				PreparedStatement obtenerDatosItem = connect.prepareStatement("SELECT * FROM item WHERE idItem = ?");
				obtenerDatosItem.setInt(1,
						paquetePersonaje.getIdItem(paquetePersonaje.getCantidadObjetosInventario() - 1));
				ResultSet resultadoDatoItem = null;
				resultadoDatoItem = obtenerDatosItem.executeQuery();
				if (resultadoDatoItem.next()) {
					paquetePersonaje.removerUltimoItem();
					paquetePersonaje.añadirItem(resultadoDatoItem.getInt("idItem"),
							resultadoDatoItem.getString("nombre"), resultadoDatoItem.getInt("bonusSalud"),
							resultadoDatoItem.getInt("bonusEnergia"), resultadoDatoItem.getInt("bonusFuerza"),
							resultadoDatoItem.getInt("bonusDestreza"), resultadoDatoItem.getInt("bonusInteligencia"),
							resultadoDatoItem.getString("nomImagen"));
				}
				PreparedStatement stActualizarMochila = connect.prepareStatement(
						"UPDATE mochila SET item1=? ,item2=? ,item3=? ,item4=? ,item5=? ,item6=? ,item7=? ,item8=? ,item9=? "
								+ ",item10=? ,item11=? ,item12=? ,item13=? ,item14=? ,item15=? ,item16=? ,item17=? ,item18=? ,item19=? ,item20=? WHERE idMochila=?");
				while (i < paquetePersonaje.getCantidadObjetosInventario()) {
					stActualizarMochila.setInt(i + 1, paquetePersonaje.getIdItem(i));
					i++;
				}
				for (int j = paquetePersonaje.getCantidadObjetosInventario(); j < 20; j++) {
					stActualizarMochila.setInt(j + 1, -1);
				}
				stActualizarMochila.setInt(21, paquetePersonaje.getId());
				stActualizarMochila.executeUpdate();
			}
			Servidor.log.append("El personaje " + paquetePersonaje.getNombre() + " se ha actualizado con éxito."
					+ System.lineSeparator());
		} catch (SQLException e) {
			Servidor.log.append("Fallo al intentar actualizar el personaje " + paquetePersonaje.getNombre()
					+ System.lineSeparator());
			e.printStackTrace();
		}
	}

	/**
	 * Devuelve al personaje con sus datos. <br>
	 * 
	 * @param user
	 *            Usuario. <br>
	 * @return Personaje. <br>
	 * @throws IOException
	 *             Error al abrir archivo. <br>
	 */
	public PaquetePersonaje getPersonaje(PaqueteUsuario user) throws IOException {
		ResultSet result = null;
		int i = 2;
		int j = 0;
		ResultSet resultadoItemsID = null;
		ResultSet resultadoDatoItem = null;
		try {
			// Selecciono el personaje de ese usuario
			PreparedStatement st = connect.prepareStatement("SELECT * FROM registro WHERE usuario = ?");
			st.setString(1, user.getUsername());
			result = st.executeQuery();
			// Obtengo el id
			int idPersonaje = result.getInt("idPersonaje");
			// Selecciono los datos del personaje
			PreparedStatement stSeleccionarPersonaje = connect
					.prepareStatement("SELECT * FROM personaje WHERE idPersonaje = ?");
			stSeleccionarPersonaje.setInt(1, idPersonaje);
			result = stSeleccionarPersonaje.executeQuery();
			PreparedStatement stDameItemsID = connect.prepareStatement("SELECT * FROM mochila WHERE idMochila = ?");
			stDameItemsID.setInt(1, idPersonaje);
			resultadoItemsID = stDameItemsID.executeQuery();
			PreparedStatement stDatosItem = connect.prepareStatement("SELECT * FROM item WHERE idItem = ?");
			// Obtengo los atributos del personaje
			PaquetePersonaje personaje = new PaquetePersonaje();
			personaje.setId(idPersonaje);
			personaje.setRaza(result.getString("raza"));
			personaje.setCasta(result.getString("casta"));
			personaje.setFuerza(result.getInt("fuerza"));
			personaje.setInteligencia(result.getInt("inteligencia"));
			personaje.setDestreza(result.getInt("destreza"));
			personaje.setEnergiaTope(result.getInt("energiaTope"));
			personaje.setSaludTope(result.getInt("saludTope"));
			personaje.setNombre(result.getString("nombre"));
			personaje.setExperiencia(result.getInt("experiencia"));
			personaje.setNivel(result.getInt("nivel"));
			while (j <= CANTIDADMAXIMAITEMS) {
				if (resultadoItemsID.getInt(i) != -1) {
					stDatosItem.setInt(1, resultadoItemsID.getInt(i));
					resultadoDatoItem = stDatosItem.executeQuery();
					personaje.añadirItem(resultadoDatoItem.getInt("idItem"), resultadoDatoItem.getString("nombre"),
							resultadoDatoItem.getInt("bonusSalud"), resultadoDatoItem.getInt("bonusEnergia"),
							resultadoDatoItem.getInt("bonusFuerza"), resultadoDatoItem.getInt("bonusDestreza"),
							resultadoDatoItem.getInt("bonusInteligencia"), resultadoDatoItem.getString("nomImagen"));
				}
				i++;
				j++;
			}
			return personaje;
		} catch (SQLException ex) {
			Servidor.log
					.append("Fallo al intentar recuperar el personaje " + user.getUsername() + System.lineSeparator());
			Servidor.log.append(ex.getMessage() + System.lineSeparator());
			ex.printStackTrace();
		}
		return new PaquetePersonaje();
	}

	/**
	 * Devuelve al usuario. <br>
	 * 
	 * @param usuario
	 *            Usuario. <br>
	 * @return Usuario. <br>
	 */
	public PaqueteUsuario getUsuario(String usuario) {
		ResultSet result = null;
		PreparedStatement st;
		try {
			st = connect.prepareStatement("SELECT * FROM registro WHERE usuario = ?");
			st.setString(1, usuario);
			result = st.executeQuery();
			String password = result.getString("password");
			int idPersonaje = result.getInt("idPersonaje");
			PaqueteUsuario paqueteUsuario = new PaqueteUsuario();
			paqueteUsuario.setUsername(usuario);
			paqueteUsuario.setPassword(password);
			paqueteUsuario.setIdPj(idPersonaje);
			return paqueteUsuario;
		} catch (SQLException e) {
			Servidor.log.append("Fallo al intentar recuperar el usuario " + usuario + System.lineSeparator());
			Servidor.log.append(e.getMessage() + System.lineSeparator());
			e.printStackTrace();
		}
		return new PaqueteUsuario();
	}

	/**
	 * Actualiza el inventario del personaje. <br>
	 * 
	 * @param paquetePersonaje
	 *            Personaje. <br>
	 */
	public void actualizarInventario(PaquetePersonaje paquetePersonaje) {
		int i = 0;
		PreparedStatement stActualizarMochila;
		try {
			stActualizarMochila = connect.prepareStatement(
					"UPDATE mochila SET item1=? ,item2=? ,item3=? ,item4=? ,item5=? ,item6=? ,item7=? ,item8=? ,item9=? "
							+ ",item10=? ,item11=? ,item12=? ,item13=? ,item14=? ,item15=? ,item16=? ,item17=? ,item18=? ,item19=? ,item20=? WHERE idMochila=?");
			while (i < paquetePersonaje.getCantidadObjetosInventario()) {
				stActualizarMochila.setInt(i + 1, paquetePersonaje.getIdItem(i));
				i++;
			}
			for (int j = paquetePersonaje.getCantidadObjetosInventario(); j < 20; j++) {
				stActualizarMochila.setInt(j + 1, -1);
			}
			stActualizarMochila.setInt(21, paquetePersonaje.getId());
			stActualizarMochila.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
