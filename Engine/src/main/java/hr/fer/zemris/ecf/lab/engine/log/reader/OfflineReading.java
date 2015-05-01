package hr.fer.zemris.ecf.lab.engine.log.reader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import hr.fer.zemris.ecf.lab.engine.log.Generation;
import hr.fer.zemris.ecf.lab.engine.log.Individual;
import hr.fer.zemris.ecf.lab.engine.log.LogModel;
import hr.fer.zemris.ecf.lab.engine.log.genotypes.GenotypeReader;
import hr.fer.zemris.ecf.lab.engine.log.genotypes.InitialGenotype;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This class is used for off-line reading a.k.a. it is used for reading when the ECF is done with computing specific task and it has finished creating log file.
 * It can gather computed generations and hall of fame. They are all put in a array list of {@link Generation} called generations and array list {@link Individual} called hallOfFame.
 * @version 1.0
 *
 */
public class OfflineReading {
	/**
	 * User of this class only needs to know to give it the path to the log file and the log file will be "magically" parsed into
	 * {@link Generation} and {@link Individual} lists stored in this class.
	 * @param file
	 * @throws Exception If reading goes wrong
	 */
	public LogModel read(String file) throws Exception {
		Scanner sc;
		try {
			sc = new Scanner(new File(file));
		} catch (FileNotFoundException e) {
			System.err.println("Can't find \"log\" file.");
			throw e;
		}

		List<Generation> generations;
		List<Individual> hallOfFame;
		try {
			generations = reading(sc);
			hallOfFame = createHallOfFame(sc);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			sc.close();
			String errPath = file + ".err";
			File errFile = new File(errPath);
			String message = "Error";
			if (errFile.exists()) {
				try {
					byte[] bytes = Files.readAllBytes(Paths.get(errPath));
					message = new String(bytes, StandardCharsets.UTF_8).trim();
					if (message.isEmpty()) {
						throw e;
					}
					bytes = Files.readAllBytes(Paths.get(file));
					message += "\n*************\n" +  new String(bytes, StandardCharsets.UTF_8).trim();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				errFile.delete();
				throw new Exception(message);
			} else {
				throw e;
			}
		}
		sc.close();
		return new LogModel(generations, hallOfFame);
	}
	
	/**
	 * This meted creates hall of fame by calling parseHallOfFame meted from this class.
	 * @param sc scanner to where the hall of fame is
	 * @throws java.io.IOException If error occurs while reading HallOfFame from log file
	 * @throws org.xml.sax.SAXException If error occurs while reading HallOfFame from log file
	 * @throws javax.xml.parsers.ParserConfigurationException If error occurs while reading HallOfFame from log file
	 */
	private List<Individual> createHallOfFame(Scanner sc) throws ParserConfigurationException, SAXException, IOException {
		StringBuilder sb = new StringBuilder();
		String line;
		while(sc.hasNextLine()) {
			line = sc.nextLine();
			if(line.contains("HallOfFame")){
				sb.append(line);
				break;
			}
		}
		while(sc.hasNextLine()){
			line = sc.nextLine();
			
			sb.append(line);
			if (line.equals("</HallOfFame>")){
				break;
			}			
		}
		if(sb.length() > 0) {
			return parseHallOfFame(sb.toString());
		}
		return new ArrayList<>();
	}
	
	/**
	 * This meted is used for reading log file and it calls {@link GenerationReading} when it creates predefined lines for {@link GenerationReading} to parse.
	 * This meted also gathers parsed form {@link GenerationReading} and puts them into generations array list.
	 * @param sc scanner for the log file
	 */
	private List<Generation> reading(Scanner sc) {
		String line = "";
		//Getting to first generation
		while(sc.hasNextLine() && !line.contains("Generation")){
			line = sc.nextLine();
		}
		
		ArrayList<String> gen = new ArrayList<>();
		gen.add(line);
		ArrayList<Generation> generations = new ArrayList<>();
		
		while(sc.hasNextLine()){
			line = sc.nextLine();
			if(line.contains("Generation")){
				generations.add(GenerationReading.parse(gen));
				gen = new ArrayList<>();
			}
			if(line.contains("Best of run")){
				generations.add(GenerationReading.parse(gen));
				gen = new ArrayList<>();
				break;
			}
			gen.add(line);
		}

		return generations;
	}

	/**
	 * This class is used for parsing only the specific part of the log file, the xml part of the log file where the hall of fame is.
	 * @param xml one big string where all of the xml is.
	 * @throws javax.xml.parsers.ParserConfigurationException if problems.
	 * @throws org.xml.sax.SAXException if problems.
	 * @throws java.io.IOException if problems.
	 */
	private List<Individual> parseHallOfFame(String xml) throws ParserConfigurationException, SAXException, IOException {
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    DocumentBuilder builder = factory.newDocumentBuilder();
	    InputSource is = new InputSource(new StringReader(xml));
	    Document doc =  builder.parse(is);
	    NodeList hofParent = doc.getChildNodes();
	    Node hof = hofParent.item(0);
	    
		//((Element) hof).getAttribute("size"); This is size
//		System.out.println("Hof  =" +((Element) hof).getAttribute("size"));
	    
		NodeList individualList = hof.getChildNodes();
		ArrayList<Individual> hallOfFame = new ArrayList<>();

		for(int i=0; i<individualList.getLength(); i++){
			Node indiv = individualList.item(i);
			Individual ind = new Individual();
			if (indiv.getNodeType() == Node.ELEMENT_NODE) {
				ind.size = Integer.parseInt(((Element) indiv).getAttribute("size").trim());
				ind.gen = Integer.parseInt(((Element) indiv).getAttribute("gen").trim());
//				System.out.println("size + gen =" +ind.size +" + "+ind.gen);
				fillIndividulal(indiv,ind);
				hallOfFame.add(ind);
			}
		}

		return hallOfFame;
	}

	/**
	 * This meted is used to fill one given {@link Individual} from the given {@link org.w3c.dom.Node}
	 * @param indiv node from witch the individual will be filled
	 * @param ind given individual to be filled
	 */
	private void fillIndividulal(Node indiv, Individual ind) {
		NodeList paramsList = indiv.getChildNodes();
		
		for(int i=0; i<paramsList.getLength(); i++){
			Node param = paramsList.item(i);			
			if (param.getNodeType() == Node.ELEMENT_NODE) {
				
				Element p = (Element) param;
				String value = p.getAttribute("value").trim();
				
				if(p.getNodeName().equals("FitnessMax")){
					ind.fitnessMax = Double.parseDouble(value);
//					System.out.println("fit max " +ind.fitnessMax);
					continue;
				}
				
				String size = p.getAttribute("size").trim();
				String name = p.getNodeName().trim();
				InitialGenotype gen = new InitialGenotype();
				if (!(size == null || size.isEmpty())) {
					gen.size = Integer.parseInt(size);
				}
				gen.name = name;
				gen.value = value.isEmpty() ? p.getTextContent() : value;
				ind.genotypes.add(GenotypeReader.getGenotype(gen));
//				System.out.println("Name + size =" +gen.name +" "+gen.size);
//				System.out.println("   Gen Value =" + gen.value);
			}
		}
		
	}

}
