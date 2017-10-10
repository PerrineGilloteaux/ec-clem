/**
 * author Perrine.Paul-Gilloteaux@univ-nantes.fr
 *  This plugin starts from a sequence (2D, 3D, time..) with several channels, 
 *  and create a new sequence where the channel content have been modified to help the visualization
 *  Several method of blending can be proposed, for now:
 *  - remove overlapping part (for mosaicking) from other channels
 *  
 */


package plugins.perrine.smartmerge;

import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.ezplug.EzVarText;
import icy.image.IcyBufferedImage;
import icy.math.ArrayMath;
import icy.sequence.Sequence;
import icy.type.collection.array.Array1DUtil;
import icy.type.collection.array.Array2DUtil;

public class SmartMerge extends EzPlug {
  EzVarSequence inputseq=new EzVarSequence("ImageTovisualize");
  private EzVarText choiceblendingmethod= new EzVarText("Method to be used",
			new String[] { "Crop (first channel, first kept)", "Max value", "averaged" }, 0, false);
	@Override
	protected void initialize() {
		addEzComponent(inputseq);
		addEzComponent(choiceblendingmethod);
	}

	@Override
	protected void execute() {
		Sequence sequence=inputseq.getValue();
		Sequence result=new Sequence(sequence.getName() +" - SmartMerge");
		result.beginUpdate();
		try
		{
			if (choiceblendingmethod.getValue()=="Crop (first channel, first kept)"){
			for (int t=0;t<sequence.getSizeT();t++){
				for (int z=0;z<sequence.getSizeZ();z++){
				IcyBufferedImage image= getBlendedfirstserved(sequence,t,z);
				result.setImage(t, z, image);
				}
			}
			}
			if (choiceblendingmethod.getValue()=="Max value"){
				for (int t=0;t<sequence.getSizeT();t++){
					for (int z=0;z<sequence.getSizeZ();z++){
					IcyBufferedImage image= getBlendedmax(sequence,t,z);
					result.setImage(t, z, image);
					}
				}
				}
			if (choiceblendingmethod.getValue()=="averaged"){
				for (int t=0;t<sequence.getSizeT();t++){
					for (int z=0;z<sequence.getSizeZ();z++){
					IcyBufferedImage image= getBlendedaverage(sequence,t,z);
					result.setImage(t, z, image);
					}
				}
				}
		}
		finally
		{
			result.endUpdate();
		}
		for (int c=0; c<sequence.getSizeC();c++){
			result.setColormap(c, sequence.getColorMap(c));
		}
		addSequence(result);
	}

	private IcyBufferedImage getBlendedfirstserved(Sequence sequence, int t,int z)
	{
		
		
		IcyBufferedImage result = new IcyBufferedImage(sequence.getSizeX(),sequence.getSizeY(),sequence.getSizeC(), sequence.getDataType_());
		double[] doubleArray= new double[sequence.getSizeX()*sequence.getSizeY()];
		double[][] imgAllCArray=new double[sequence.getSizeC()][doubleArray.length];
		
		Object dataArray=sequence.getDataXYC(t, z);
		
			
			
		imgAllCArray=Array2DUtil.arrayToDoubleArray(dataArray, sequence.isSignedDataType());
			
		
		// remove overlapped area
		for (int c=0; c<sequence.getSizeC();c++){
			
			for (int i=0;i<doubleArray.length;i++){
				
				if (doubleArray[i]>0){
					doubleArray[i]=0;
				    
				}
				else{
					
					doubleArray[i]=imgAllCArray[c][i];
					for (int pc=0;pc<c;pc++){
						if (imgAllCArray[pc][i]>0){
							doubleArray[i]=0;
							break;
						}
					}
				}
					
			}

			//ArrayMath.max(doubleArray,imgDoubleArray,doubleArray2);
			//ArrayMath.subtract(doubleArray2,doubleArray,doubleArray);
			Array1DUtil.doubleArrayToArray(doubleArray,result.getDataXY(c))	;
		}
			
		
		result.dataChanged();
		return result;
		
	}
	
	private IcyBufferedImage getBlendedmax(Sequence sequence, int t,int z)
	{
		
		
		IcyBufferedImage result = new IcyBufferedImage(sequence.getSizeX(),sequence.getSizeY(),sequence.getSizeC()+1, sequence.getDataType_());
		double[] doubleArray= new double[sequence.getSizeX()*sequence.getSizeY()];
		double[][] imgAllCArray=new double[sequence.getSizeC()][doubleArray.length];
		
		Object dataArray=sequence.getDataXYC(t, z);
		
			
			
		imgAllCArray=Array2DUtil.arrayToDoubleArray(dataArray, sequence.isSignedDataType());
			
		double[] maxchannel=new double[doubleArray.length];
		
		for (int c=0;c<sequence.getSizeC();c++){
			for (int i=0;i<doubleArray.length;i++){
				// detect overlap
				if ((imgAllCArray[c][i]!=0))
				{
						doubleArray[i]+=1;
						
					    if (imgAllCArray[c][i]>maxchannel[i])
							maxchannel[i]=imgAllCArray[c][i];
				}
			}
		}
		for (int c=0;c<sequence.getSizeC();c++){
			for (int i=0;i<doubleArray.length;i++){
				if (doubleArray[i]>1){
					imgAllCArray[c][i]=0;
					
				}
				else 
					maxchannel[i]=0;
			}	
			Array1DUtil.doubleArrayToArray(imgAllCArray[c],result.getDataXY(c))	;
		
		}
	
		Array1DUtil.doubleArrayToArray(maxchannel,result.getDataXY(sequence.getSizeC()))	;
		result.dataChanged();
		return result;
		
	}
	private IcyBufferedImage getBlendedaverage(Sequence sequence, int t,int z)
	{
		
		
		IcyBufferedImage result = new IcyBufferedImage(sequence.getSizeX(),sequence.getSizeY(),sequence.getSizeC()+1, sequence.getDataType_());
		double[] doubleArray= new double[sequence.getSizeX()*sequence.getSizeY()];
		double[][] imgAllCArray=new double[sequence.getSizeC()][doubleArray.length];
		
		Object dataArray=sequence.getDataXYC(t, z);
		
			
			
		imgAllCArray=Array2DUtil.arrayToDoubleArray(dataArray, sequence.isSignedDataType());
			
		double[] maxchannel=new double[doubleArray.length];
		
		
			for (int i=0;i<doubleArray.length;i++){
				for (int c=0;c<sequence.getSizeC();c++){
				// detect overlap
				if ((imgAllCArray[c][i]!=0))
				{
						doubleArray[i]+=1;
						maxchannel[i]+=imgAllCArray[c][i];
				}
			}
				if (maxchannel[i]>0)
					maxchannel[i]=maxchannel[i]/doubleArray[i];
				
		}
		for (int c=0;c<sequence.getSizeC();c++){
			for (int i=0;i<doubleArray.length;i++){
				if (doubleArray[i]>1){
					imgAllCArray[c][i]=0;
					
				}
				else 
					maxchannel[i]=0;
			}	
			Array1DUtil.doubleArrayToArray(imgAllCArray[c],result.getDataXY(c))	;
		
		}
	
		Array1DUtil.doubleArrayToArray(maxchannel,result.getDataXY(sequence.getSizeC()))	;
		result.dataChanged();
		return result;
		
	}

	@Override
	public void clean() {
		// TODO Auto-generated by Icy4Eclipse
	}
}
