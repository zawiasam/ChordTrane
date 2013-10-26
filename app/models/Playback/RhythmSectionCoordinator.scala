package models


case class ComperTemplate(chordGenerator: ChordGenerator.ChordGenerator, tick: Int)


object RhythmSectionCoordinator{

	//Chords with their literal positions in music
	def generateChordTemplate(measures: List[Measure], timeSig: Int, subdivisions: Int) = {
		val ticksPerMeasure = subdivisions * timeSig

		//Convert beat to linear time position
		def beatToTick(beat: Int, measureIndex: Int) = {
			(beat -1) * subdivisions + ticksPerMeasure * measureIndex
		}

		measures.zipWithIndex.flatMap{
			case (measure, index) => {
				measure.chords.map(chord => ComperTemplate(ChordLogic.getChordGenerator(chord),beatToTick(chord.beat, index)))
			}
		}.toArray
	}

	val dummyMeasures = Helper.measuresOnly(Master.parseSong(Master.DummySong))

	val dummyRhythmSection = new RhythmSectionCoordinator(dummyMeasures, 4, 2, PianoPlayer.JAZZ_SWING4)

	


}

class RhythmSectionCoordinator(measures: List[Measure], timeSig: Int, subdivisions: Int, style: Int){

	val chordTemplate = RhythmSectionCoordinator.generateChordTemplate(measures, timeSig, subdivisions)

	def piano = PianoPlayer.getCompingMidi(chordTemplate, style, measures.length)

	def bass = BassPlayer.getBass(chordTemplate)

	def drums = Drums.SwingDrumBasic(measures.length)

	def midi(path: String){
		val master = new MidiCreator
		


		//Piano
		val pianoMidiData = MidiCreator.PianoProgram(MidiCreator.PianoChannel) +: MidiCreator.midiChordsToMidiEvent(piano, 3, MidiCreator.PianoChannel) //piano triplet feel. Resolve later
		master.createTrack(pianoMidiData)

		//Bass 
		val bassMidiData = MidiCreator.BassProgram(MidiCreator.BassChannel) +: MidiCreator.singleNotesToMidiEvent(bass, subdivisions, MidiCreator.BassChannel) 
		master.createTrack(bassMidiData)

		//Drums 
		val drumMidiData = MidiCreator.DrumProgram(MidiCreator.DrumChannel) +: MidiCreator.singleNotesToMidiEvent(drums, 3, MidiCreator.DrumChannel) 
		master.createTrack(drumMidiData)

		master.createMidi(path)
	}








}