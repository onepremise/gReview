[#-- @ftlvariable name="repository" type="com.atlassian.bamboo.plugins.git.GitRepository" --]

<script type="text/javascript">
    var customBranch="";
    
    function log(msg) {
        console.log(msg);
    }
    
    AJS.$(document).ready(function() {
      enableCustomField();
    });
    
    function setCustomBranch(branch) {
      customBranch=branch;
    }

    function enableCustomField() {
      var $sc = document.getElementById("repositoryGerritDefaultBranch");
      var $txtField = document.getElementById("repositoryGerritCustomBranch");
      var $indx=$sc.selectedIndex;
      var $options=$sc.options;
      
      if (!customBranch) {
          setCustomBranch($txtField.value)
      }
      
      if ($options[$indx].text === 'Custom') {
          $txtField.disabled=false;
          if (customBranch) {
            $txtField.value=customBranch
          }
      } else {
          $txtField.disabled=true;
          $txtField.value=$options[$indx].text
      }
    }
</script>

<table class="aui">
  <tbody>
    <tr>
      <td>[@ww.select name='repository.gerrit.default.branch' id='repositoryGerritDefaultBranch' label='Default Branch' maxlength='50' size='1' onchange='enableCustomField();' list="{'master','All branches','Custom'}" /]</td>
      <td>[@ww.textfield name='repository.gerrit.custom.branch' id='repositoryGerritCustomBranch' labelKey='Custom Branch' disabled='true' onchange='setCustomBranch(this.value);' /]</td>
    </tr>
  </tbody>
</table>

[@ww.checkbox labelKey='repository.gerrit.useSubmodules' name='repository.gerrit.useSubmodules' /]
[@ww.textfield labelKey='repository.gerrit.commandTimeout' name='repository.gerrit.commandTimeout' /]
[@ww.checkbox labelKey='repository.gerrit.verbose.logs' name='repository.gerrit.verbose.logs' /]
